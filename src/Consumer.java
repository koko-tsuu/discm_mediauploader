import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;


public class Consumer {
    static ObjectOutputStream objectOutputStream;
    static ObjectInputStream objectInputStream;
    static Integer maxQueueLength;

    volatile static ArrayList<ConsumerThread> consumerThreadsList = new ArrayList<>();
    volatile static Dictionary<String, Integer> filesInProgress = new Hashtable<>();
    volatile static Dictionary<String, ArrayList<byte[]>> queueBytesDictionary = new Hashtable<>();
    volatile static ArrayList<String> queueOrder = new ArrayList<>();
    volatile static Thread listenerThread;
    volatile static Thread assignerThread;

    volatile static ArrayList<Message> messageQueue = new ArrayList<>();

    volatile static boolean allFilesSubmitted = false;

    static class ListenerThread implements Runnable {
        // Overriding the run Method
        @Override
        public void run() {
            while (!allFilesSubmitted) {
                try {
                    // 2: wait for request from producer
                    Message messageFromProducer = (Message) objectInputStream.readObject();

                    modifyMessageQueue(0, messageFromProducer);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static class AssignerThread implements Runnable {
        @Override
        public void run() {
            while (!allFilesSubmitted) {

                if (!messageQueue.isEmpty()) {
                    System.out.println("Message received: " + messageQueue.getFirst().toString());
                    Message messageFromProducer = messageQueue.getFirst();
                    StatusCode statusCode = messageFromProducer.getStatusCode();
                    String messageFilename = messageFromProducer.getFilename();
                    byte[] messageFileData = messageFromProducer.getBytesToSendArray();

                    try {
                        // 3: check message type
                        if (statusCode == StatusCode.REQUEST) {

                            // file currently being handled by a thread
                            Integer threadIndex = filesInProgress.get(messageFilename);
                            if (threadIndex != null) {
                                consumerThreadsList.get(threadIndex).modifyBuffer(0, messageFromProducer);
                            }

                            // if file currently exists in the queue
                            else if (queueBytesDictionary.get(messageFilename) != null) {
                                modifyQueueBytesDictionary(2, messageFilename, messageFileData);
                            }

                            // queue is full
                            else if (queueOrder.size() >= maxQueueLength) {
                                Message queuedMessage = new Message(StatusCode.QUEUE_FULL);
                                objectOutputStream.writeObject(queuedMessage);
                                objectOutputStream.flush();
                            }

                            // put a new request in queue
                            else {
                                addQueue(messageFilename, messageFileData);


                            }

                        } else if (statusCode == StatusCode.FILE_COMPLETE) {
                            Integer index = filesInProgress.get(messageFilename);

                            // file is done downloading
                            if (index != null) {
                                modifyFilesInProgress(1, messageFilename, -1);
                            }


                        } else if (statusCode == StatusCode.FILE_ALL_COMPLETE) {
                            Message queuedMessage = new Message(StatusCode.FILE_ALL_COMPLETE);
                            objectOutputStream.writeObject(queuedMessage);
                            objectOutputStream.flush();

                            for (int i = 0; i < consumerThreadsList.size(); i++) {
                                consumerThreadsList.get(i).shutdown();
                            }

                            allFilesSubmitted = true;
                            break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    modifyMessageQueue(1, null);
                }
            }
        }
    }

    synchronized static void modifyMessageQueue(int type, Message message) {
        if (type == 0) {
            messageQueue.add(message);
        } else if (type == 1) {
            messageQueue.removeFirst();
        }
        System.out.println("Current messages: " + messageQueue.toString());
    }

    synchronized static void modifyQueueBytesDictionary(int type, String filename, byte[] bytesToSendArray) {
        if (type == 0) {
            queueBytesDictionary.put(filename, new ArrayList<>());
            queueBytesDictionary.get(filename).add(bytesToSendArray);
        } else if (type == 1) {
            queueBytesDictionary.remove(filename);
        } else if (type == 2) {
            queueBytesDictionary.get(filename).add(bytesToSendArray);
        }
    }

    synchronized static void modifyQueueOrder(int type, String filename, int index) {
        if (type == 0) {
            queueOrder.add(filename);
        } else if (type == 1) {
            queueOrder.remove(index);
        }

    }

    synchronized static void modifyFilesInProgress(int type, String filename, int threadAssigned) {
        if (type == 0) {
            filesInProgress.put(filename, threadAssigned);
        } else if (type == 1) {
            filesInProgress.remove(filename);
        }
    }

    synchronized static void removeQueue(String filename, int indexToRemove) {
        modifyQueueBytesDictionary(1, filename, null);
        modifyQueueOrder(1, null, indexToRemove);
    }

    synchronized static void addQueue(String messageFilename, byte[] messageFileData)
    {
        modifyQueueOrder(0, messageFilename, -1);
        modifyQueueBytesDictionary(0, messageFilename, messageFileData);
    }




    public static void main(String[] args) {

        System.out.print("Number of consumer threads: 1");
        Scanner scanner = new Scanner(System.in);
        int consumerThreadsNum = 1;// scanner.nextInt();


        System.out.print("Max queue length: 5");
        maxQueueLength = 5; //scanner.nextInt();
        scanner.close();

        try {
            Files.createDirectory(Paths.get(System.getProperty("user.dir") + "\\output"));
        } catch (IOException e) {
           // System.out.println("Could not create directory as output directory may already exist.");
        }

        // 1: create a socket to connect to
        try {
            ServerSocket serverSocket = new ServerSocket(3000);

            System.out.println("Hosting server: " + serverSocket.getInetAddress().getHostAddress());
            Socket socket = serverSocket.accept();

            System.out.println("Client has connected.");

            objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            objectInputStream = new ObjectInputStream(socket.getInputStream());

            listenerThread = new Thread(new ListenerThread());
            listenerThread.start();

            assignerThread = new Thread(new AssignerThread());
            assignerThread.start();



            for (int i = 0; i < consumerThreadsNum; i++) {
                consumerThreadsList.add(new ConsumerThread());
            }

            while (!allFilesSubmitted) {

                // it is assumed that all items in the queue are new items
                for (int i = 0; i < queueOrder.size(); i++) {

                    // file currently being handled by a thread
                    Integer threadIndex = filesInProgress.get(queueOrder.get(i));
                    if (threadIndex != null) {
                        ArrayList<byte[]> fileBytes = queueBytesDictionary.get(queueOrder.get(i));
                        for (int k = 0; i < fileBytes.size(); k++) {
                            Message message = new Message(StatusCode.REQUEST, fileBytes.get(k));
                            consumerThreadsList.get(threadIndex).modifyBuffer(0, message);

                        }

                        removeQueue(queueOrder.get(i), i);

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
                                modifyFilesInProgress(0, filename, j);

                                ArrayList<byte[]> fileBytesToBeHandled = queueBytesDictionary.get(filename);

                                // add all bytes
                                for (int k = 0; i < fileBytesToBeHandled.size(); k++) {

                                    Message message = new Message(StatusCode.REQUEST, fileBytesToBeHandled.get(k));
                                    consumerThreadsList.get(j).modifyBuffer(0, message);

                                }

                                removeQueue(filename, i);
                                i--;
                                break;
                            }


                        }
                    }
                }

            }
            System.out.println("Finished receiving all files. Shutting down.");

            listenerThread.join();
            assignerThread.join();
            serverSocket.close();


        } catch (Exception e) {
            e.printStackTrace();
        }


    }


}

