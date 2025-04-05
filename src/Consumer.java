import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;

/*
    NOTE: Consumer already automatically creates an output directory if it doesn't exist
 */

public class Consumer {
    // Shared stream
    static ObjectOutputStream objectOutputStream;
    static ObjectInputStream objectInputStream;

    static Integer maxQueueLength;

    volatile static ArrayList<ConsumerThread> consumerThreadsList = new ArrayList<>();

    // files that are being downloaded / handled by threads currently
    volatile static Dictionary<String, Integer> filesInProgress = new Hashtable<>();
    // queued files
    volatile static Dictionary<String, ArrayList<Message>> queueDictionary = new Hashtable<>();
    // this is connected to queueDictionary
    volatile static ArrayList<String> queueOrder = new ArrayList<>();
    // files to be ignored due to queue being full
    volatile static Dictionary<String, Boolean> ignoreFileDictionary = new Hashtable<>();

    // thread holders
    volatile static Thread listenerThread;
    volatile static Thread assignerThread;

    // received the message from the Producer that it has finished sending its file
    volatile static boolean receivedFileAllCompleteMessage = false;

    // messages from Producer queued
    volatile static ArrayList<Message> messageQueue = new ArrayList<>();

    //
    volatile static boolean allFilesDownloaded = false;

    static MainWindow mainWindow;


    static class ConsumerGUI implements Runnable {
        @Override
        public void run() {
            mainWindow = new MainWindow();
            ConsumerThread.setMainWindow(mainWindow);
            mainWindow.show();
        }
    }


    static class ListenerThread implements Runnable {
        // Overriding the run Method
        @Override
        public void run() {
            while (!receivedFileAllCompleteMessage) {
                try {
                    // 2: wait for request from producer
                    Message messageFromProducer = (Message) objectInputStream.readObject();

                    if (messageFromProducer.getStatusCode() == StatusCode.FILE_ALL_COMPLETE) {
                        receivedFileAllCompleteMessage = true;
                    }
                    else
                        modifyMessageQueue(ModifyMessageQueueType.APPEND, messageFromProducer);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static class AssignerThread implements Runnable {

        void messageQueueAssigner()
        {

            // message queue handling
            if (!messageQueue.isEmpty()) {
                Message messageFromProducer = messageQueue.get(0);
                StatusCode statusCode = messageFromProducer.getStatusCode();
                String messageFilename = messageFromProducer.getFilename();

                try {
                    // 3: check message type
                    if (statusCode == StatusCode.REQUEST) {

                        // if file is not in the blacklist
                        if (ignoreFileDictionary.get(messageFilename) == null) {

                            // file currently being handled by a thread
                            Integer threadIndex = filesInProgress.get(messageFilename);
                            if (threadIndex != null) {
                                consumerThreadsList.get(threadIndex).modifyBuffer(ConsumerThread.ModifyBufferType.APPEND, messageFromProducer);
                            }

                            // if file currently exists in the queue
                            else if (queueDictionary.get(messageFilename) != null) {
                                modifyQueueDictionary(ModifyQueueDictionaryType.APPEND, messageFromProducer);
                            }

                            // queue is full
                            else if (queueOrder.size() >= maxQueueLength) {
                                try {
                                    // blacklist the file
                                    System.out.println("Queue is full. File [" + messageFilename + "] will no longer be downloaded and packets related to it will be ignored.");
                                    modifyIgnoreFileDictionary(ModifyIgnoreFileDictionary.ADD, messageFilename);
                                    Message queuedMessage = new Message(StatusCode.QUEUE_FULL, messageFilename);
                                    objectOutputStream.writeObject(queuedMessage);
                                    objectOutputStream.flush();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            // put a new request in queue
                            else {
                                addQueue(messageFromProducer);


                            }
                        }

                    } else if (statusCode == StatusCode.FILE_COMPLETE) {

                        // file is not blacklisted
                        if (ignoreFileDictionary.get(messageFilename) == null) {
                            Integer filesInProgressIndex = filesInProgress.get(messageFilename);
                            boolean queueBytesIndexExists = queueDictionary.get(messageFilename) != null;

                            // file is done downloading and exists in our filesInProgress
                            // remove it from filesInProgress and put in the buffer of the thread
                            if (filesInProgressIndex != null) {
                                consumerThreadsList.get(filesInProgressIndex).modifyBuffer(ConsumerThread.ModifyBufferType.APPEND, messageFromProducer);
                                modifyFilesInProgress(ModifyFilesInProgressType.REMOVE, messageFilename, -1);

                            // else just put it into our queue
                            } else if (queueBytesIndexExists) {
                                modifyQueueDictionary(ModifyQueueDictionaryType.APPEND, messageFromProducer);
                            }
                        }

                        // remove from blacklist
                        else {
                            modifyIgnoreFileDictionary(ModifyIgnoreFileDictionary.REMOVE, messageFilename);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                modifyMessageQueue(ModifyMessageQueueType.POP, null);
            }

            // wait until everything is done, this will check if there's still anything it missed
            if (queueDictionary.isEmpty() && queueOrder.isEmpty() && receivedFileAllCompleteMessage) {
                boolean isDone = true;
                for (int i = 0; i < consumerThreadsList.size(); i++) {
                    if (consumerThreadsList.get(i).getFileName() != null)
                    {
                        isDone = false;
                    }
                }

                // All threads are done, we can safely join all threads
                if (isDone) {
                    try {
                        Message queuedMessage = new Message(StatusCode.FILE_ALL_COMPLETE);
                        objectOutputStream.writeObject(queuedMessage);
                        objectOutputStream.flush();

                        for (int i = 0; i < consumerThreadsList.size(); i++) {
                            consumerThreadsList.get(i).shutdown();
                        }

                        allFilesDownloaded = true;

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        void queueOrderAssigner()
        {
            for (int i = 0; i < queueOrder.size(); i++) {

                // file currently being handled by an existing thread
                Integer threadIndex = filesInProgress.get(queueOrder.get(i));
                if (threadIndex != null) {
                    ArrayList<Message> messageArrayList = queueDictionary.get(queueOrder.get(i));
                    for (int k = 0; i < messageArrayList.size(); k++) {
                        consumerThreadsList.get(threadIndex).modifyBuffer(ConsumerThread.ModifyBufferType.APPEND, messageArrayList.get(k));
                    }
                    removeQueue(queueOrder.get(i), i);
                    i--;

                }

                // try to assign the file to a free thread
                else {
                    for (int j = 0; j < consumerThreadsList.size(); j++) {
                        // is consumer thread free? if so, assign
                        if (consumerThreadsList.get(j).getFileName() == null) {

                            // get filename of the file we're handling
                            String filename = queueOrder.get(i);

                            // new file
                            consumerThreadsList.get(j).assignNewFile(filename);
                            // add to currently handling files
                            modifyFilesInProgress(ModifyFilesInProgressType.ADD_ENTRY, filename, j);

                            ArrayList<Message> messageArrayList = queueDictionary.get(filename);

                            // add all bytes
                            for (int k = 0; k < messageArrayList.size(); k++) {
                                consumerThreadsList.get(j).modifyBuffer(ConsumerThread.ModifyBufferType.APPEND, messageArrayList.get(k));

                            }
                            removeQueue(filename, i);
                            i--;
                            break;
                        }


                    }
                }
            }
        }
        @Override
        public void run() {
            while (!allFilesDownloaded) {

                messageQueueAssigner();
                // assigning for queue related stuff
                queueOrderAssigner();

            }
        }
    }

    enum ModifyMessageQueueType {
        APPEND,
        POP
    }

    synchronized static void modifyMessageQueue(ModifyMessageQueueType type, Message message) {
        if (type == ModifyMessageQueueType.APPEND) {
            messageQueue.add(message);
        } else if (type == ModifyMessageQueueType.POP) {
            messageQueue.remove(0);
        }
    }

    enum ModifyQueueDictionaryType{
        INITIALIZE_AND_APPEND,
        REMOVE,
        APPEND
    }

    synchronized static void modifyQueueDictionary(ModifyQueueDictionaryType type, Message message) {
        String filename = message.getFilename();
        if (type == ModifyQueueDictionaryType.INITIALIZE_AND_APPEND) {
            queueDictionary.put(filename, new ArrayList<>());
            queueDictionary.get(filename).add(message);
        } else if (type == ModifyQueueDictionaryType.REMOVE) {
            queueDictionary.remove(filename);
        } else if (type == ModifyQueueDictionaryType.APPEND) {
            queueDictionary.get(filename).add(message);
        }
    }

    enum ModifyQueueOrderType{
        APPEND,
        REMOVE
    }

    synchronized static void modifyQueueOrder(ModifyQueueOrderType type, String filename, int index) {
        if (type == ModifyQueueOrderType.APPEND) {
            queueOrder.add(filename);
        } else if (type == ModifyQueueOrderType.REMOVE) {
            queueOrder.remove(index);
        }

    }

    enum ModifyFilesInProgressType {
        ADD_ENTRY,
        REMOVE
    }

    synchronized static void modifyFilesInProgress(ModifyFilesInProgressType type, String filename, int threadAssigned) {
        if (type == ModifyFilesInProgressType.ADD_ENTRY) {
            filesInProgress.put(filename, threadAssigned);
        } else if (type == ModifyFilesInProgressType.REMOVE) {
            filesInProgress.remove(filename);
        }
    }

    enum ModifyIgnoreFileDictionary
    {
        ADD,
        REMOVE
    }

    synchronized static void modifyIgnoreFileDictionary(ModifyIgnoreFileDictionary type, String filename)
    {
        if (type == ModifyIgnoreFileDictionary.ADD) {
            ignoreFileDictionary.put(filename, true);
        }
        else if (type == ModifyIgnoreFileDictionary.REMOVE) {
            ignoreFileDictionary.remove(filename);
        }
    }

    synchronized static void removeQueue(String filename, int indexToRemove) {
        modifyQueueDictionary(ModifyQueueDictionaryType.REMOVE, new Message(null, filename));
        modifyQueueOrder(ModifyQueueOrderType.REMOVE, null, indexToRemove);
    }

    synchronized static void addQueue(Message message)
    {
        modifyQueueOrder(ModifyQueueOrderType.APPEND, message.getFilename(), -1);
        modifyQueueDictionary(ModifyQueueDictionaryType.INITIALIZE_AND_APPEND,  message);
    }



    public static void main(String[] args) {

        System.out.print("Number of consumer threads: ");
        Scanner scanner = new Scanner(System.in);
        int consumerThreadsNum = scanner.nextInt();


        System.out.print("Max queue length: ");
        maxQueueLength = scanner.nextInt();
        scanner.close();

        try {
            Files.createDirectory(Paths.get(System.getProperty("user.dir") + "\\output"));
        } catch (IOException e) {
            System.out.println("Could not create directory as output directory may already exist.");
        }

        try {
            Files.createDirectory(Paths.get(System.getProperty("user.dir") + "\\output\\compressed"));
        } catch (IOException e) {
            System.out.println("Could not create directory as compressed directory inside of output folder may already exist.");
        }

        // 1: create a socket to connect to
        Socket socket = null;
        boolean isConnected = false;

        while (!isConnected) {
            // this will retry until connected
            try {
                socket = new Socket("localhost", 3000);

                try {
                    isConnected = true;
                    System.out.println("Connected to server.");

                    objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                    objectInputStream = new ObjectInputStream(socket.getInputStream());

                    listenerThread = new Thread(new ListenerThread());
                    listenerThread.start();

                    assignerThread = new Thread(new AssignerThread());
                    assignerThread.start();

                    Thread consumerGUIThread = new Thread(new ConsumerGUI());
                    consumerGUIThread.start();


                    for (int i = 0; i < consumerThreadsNum; i++) {
                        consumerThreadsList.add(new ConsumerThread());
                    }


                    // wait until all threads are done
                    listenerThread.join();
                    assignerThread.join();

                    System.out.println("Finished receiving all files. Shutting down.");


                    socket.close();


                    } catch (Exception e) {
                        e.printStackTrace();
                    }

            } catch (Exception e) {
                System.out.println("Could not connect to server. Retrying in 3 seconds.");
                try {
                    TimeUnit.SECONDS.sleep(3);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

    }


}

