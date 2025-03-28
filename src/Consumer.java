import java.io.*;
import java.lang.reflect.Array;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;


public class Consumer {
    static ObjectOutputStream objectOutputStream;
    static ObjectInputStream objectInputStream;
    static Integer maxQueueLength;

    volatile static ArrayList<ConsumerThread> consumerThreadsList = new ArrayList<>();
    volatile static Dictionary<String, Integer> filesInProgress = new Hashtable<>();
    volatile static Dictionary<String, ArrayList<Message>> queueDictionary = new Hashtable<>();
    volatile static Dictionary<String, Boolean> ignoreFileDictionary = new Hashtable<>();
    volatile static ArrayList<String> queueOrder = new ArrayList<>();
    volatile static Thread listenerThread;
    volatile static Thread assignerThread;
    volatile static boolean receivedFileComplete = false;

    volatile static ArrayList<Message> messageQueue = new ArrayList<>();

    volatile static boolean allFilesSubmitted = false;

    static class ListenerThread implements Runnable {
        // Overriding the run Method
        @Override
        public void run() {
            while (!receivedFileComplete) {
                try {
                    // 2: wait for request from producer
                    Message messageFromProducer = (Message) objectInputStream.readObject();

                    if (messageFromProducer.getStatusCode() == StatusCode.FILE_ALL_COMPLETE) {
                        receivedFileComplete = true;
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
                Message messageFromProducer = messageQueue.getFirst();
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

                        // file is not blacklist
                        if (ignoreFileDictionary.get(messageFilename) == null) {
                            Integer filesInProgressIndex = filesInProgress.get(messageFilename);
                            boolean queueBytesIndexExists = queueDictionary.get(messageFilename) != null;

                            // file is done downloading and exists in our filesInProgress
                            if (filesInProgressIndex != null) {
                                consumerThreadsList.get(filesInProgressIndex).modifyBuffer(ConsumerThread.ModifyBufferType.APPEND, messageFromProducer);
                                modifyFilesInProgress(ModifyFilesInProgressType.REMOVE, messageFilename, -1);
                                // else just put it into our queue
                            } else if (queueBytesIndexExists) {
                                modifyQueueDictionary(ModifyQueueDictionaryType.APPEND, messageFromProducer);
                            }
                        }
                        else {
                            modifyIgnoreFileDictionary(ModifyIgnoreFileDictionary.REMOVE, messageFilename);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                modifyMessageQueue(ModifyMessageQueueType.POP, null);
            }

            // wait until everything is done
            if (queueDictionary.isEmpty() && queueOrder.isEmpty() && receivedFileComplete) {
                boolean isDone = true;
                for (int i = 0; i < consumerThreadsList.size(); i++) {
                    if (consumerThreadsList.get(i).getFileName() != null)
                    {
                        isDone = false;
                    }
                }

                if (isDone) {
                    try {
                        Message queuedMessage = new Message(StatusCode.FILE_ALL_COMPLETE);
                        objectOutputStream.writeObject(queuedMessage);
                        objectOutputStream.flush();

                        for (int i = 0; i < consumerThreadsList.size(); i++) {
                            consumerThreadsList.get(i).shutdown();
                        }

                        allFilesSubmitted = true;

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        void queueOrderAssigner()
        {
            for (int i = 0; i < queueOrder.size(); i++) {

                // file currently being handled by a thread
                Integer threadIndex = filesInProgress.get(queueOrder.get(i));
                if (threadIndex != null) {
                    ArrayList<Message> messageArrayList = queueDictionary.get(queueOrder.get(i));
                    for (int k = 0; i < messageArrayList.size(); k++) {
                        consumerThreadsList.get(threadIndex).modifyBuffer(ConsumerThread.ModifyBufferType.APPEND, messageArrayList.get(k));
                    }
                    removeQueue(queueOrder.get(i), i);
                    i--;

                }
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
            while (!allFilesSubmitted) {

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
            messageQueue.removeFirst();
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

        System.out.print("Number of consumer threads: 1\n");
        Scanner scanner = new Scanner(System.in);
        int consumerThreadsNum = 1;// scanner.nextInt();


        System.out.print("Max queue length: 5\n");
        maxQueueLength = 1; //scanner.nextInt();
        scanner.close();

        try {
            Files.createDirectory(Paths.get(System.getProperty("user.dir") + "\\output"));
        } catch (IOException e) {
            // System.out.println("Could not create directory as output directory may already exist.");
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

                    assert socket != null;
                    objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                    objectInputStream = new ObjectInputStream(socket.getInputStream());

                    listenerThread = new Thread(new ListenerThread());
                    listenerThread.start();

                    assignerThread = new Thread(new AssignerThread());
                    assignerThread.start();


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

