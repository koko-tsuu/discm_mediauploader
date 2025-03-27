import java.io.*;
import java.lang.reflect.Array;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;


public class Consumer {
    static ObjectOutputStream objectOutputStream;
    static ObjectInputStream objectInputStream;
    static Integer maxQueueLength;

    static ArrayList<ConsumerThread> consumerThreadsListInfo = new ArrayList<>();
    static ArrayList<Thread> consumerThreadsList = new ArrayList<>();
    static Dictionary<String, Integer> currentlyHandlingFiles = new Hashtable<>();
    static Dictionary<String, ArrayList<byte[]>> currentlyHandlingQueue = new Hashtable<>();
    static ArrayList<String> currentlyHandlingQueueOrder = new ArrayList<>();
    static Thread listenerThread;
    static Thread assignerThread;

    static ArrayList<Message> messageQueue = new ArrayList<>();

    static boolean allFilesSubmitted = false;

    static class ListenerThread implements Runnable {
        // Overriding the run Method
        @Override
        public void run() {
            while (true) {
                try {
                    // 2: wait for request from producer
                    Message messageFromProducer = (Message) objectInputStream.readObject();
                    System.out.println("Message received: " + messageFromProducer.toString());
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
                    Message messageFromProducer = messageQueue.getFirst();
                    StatusCode statusCode = messageFromProducer.getStatusCode();

                    try {
                        // 3: check message type
                        if (statusCode == StatusCode.REQUEST) {

                            // file currently being handled by a thread
                            Integer threadIndex = currentlyHandlingFiles.get(messageFromProducer.getFilename());
                            if (threadIndex != null) {
                                consumerThreadsListInfo.get(threadIndex).modifyBuffer(0, messageFromProducer);
                            }

                            // if file currently exists in the queue
                            else if (currentlyHandlingQueue.get(messageFromProducer.getFilename()) != null) {

                                currentlyHandlingQueue.get(messageFromProducer.getFilename()).add(messageFromProducer.getBytesToSendArray());
                            }

                            // queue if full
                            else if (currentlyHandlingQueueOrder.size() >= maxQueueLength) {
                                Message queuedMessage = new Message(StatusCode.QUEUE_FULL);
                                objectOutputStream.writeObject(queuedMessage);
                                objectOutputStream.flush();
                            }

                            // put a new request in queue
                            else {

                                modifyCurrentlyHandlingQueueOrder(0, messageFromProducer.getFilename(), -1);
                                modifyCurrentlyHandlingQueue(0, messageFromProducer.getFilename(), messageFromProducer.getBytesToSendArray());


                            }

                        } else if (statusCode == StatusCode.FILE_COMPLETE) {
                            Integer index = currentlyHandlingFiles.get(messageFromProducer.getFilename());

                            // file is done downloading
                            if (index != null) {
                                modifyCurrentlyHandlingFiles(1, messageFromProducer.getFilename(), -1);
                            }


                        } else if (statusCode == StatusCode.FILE_ALL_COMPLETE) {
                            Message queuedMessage = new Message(StatusCode.FILE_ALL_COMPLETE);
                            objectOutputStream.writeObject(queuedMessage);
                            objectOutputStream.flush();

                            for (int i = 0; i < consumerThreadsListInfo.size(); i++) {
                                consumerThreadsListInfo.get(i).shutdown();
                                consumerThreadsList.get(i).join();
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
        if (type == 0)
        {
            messageQueue.add(message);
        }
        else if (type == 1)
        {
            messageQueue.removeFirst();
        }
    }

    synchronized static void modifyCurrentlyHandlingQueue(int type, String filename, byte[] bytesToSendArray) {
        if (type == 0)
        {
            currentlyHandlingQueue.put(filename, new ArrayList<>());
            currentlyHandlingQueue.get(filename).add(bytesToSendArray);
        }
        else if (type == 1)
        {
            currentlyHandlingQueue.remove(filename);
        }
    }

    synchronized static void modifyCurrentlyHandlingQueueOrder(int type, String filename, int index) {
        if (type == 0)
        {
            currentlyHandlingQueueOrder.add(filename);
        }
        else if (type == 1)
        {
            currentlyHandlingQueueOrder.remove(index);
        }

    }

    synchronized static void modifyCurrentlyHandlingFiles(int type, String filename, int threadAssigned) {
        if (type == 0)
        {
            currentlyHandlingFiles.put(filename, threadAssigned);
        }
        else if (type == 1)
        {
            currentlyHandlingFiles.remove(filename);
        }
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
                consumerThreadsListInfo.add(new ConsumerThread(i));
                consumerThreadsList.add(new Thread(consumerThreadsListInfo.get(i).new thread()));
                consumerThreadsList.get(i).start();
            }

            while (!allFilesSubmitted) {
                // it is assumed that all items in the queue are new items
                for (int i = 0; i < currentlyHandlingQueueOrder.size(); i++) {
                    for (int j = 0; j < consumerThreadsListInfo.size(); j++) {
                        // is consumer thread free? if so, assign
                        if (consumerThreadsListInfo.get(j).getFileName() == null) {

                            String filename = currentlyHandlingQueueOrder.get(i);

                            // new file
                            consumerThreadsListInfo.get(j).assignNewFile(filename);
                            // add to currently handling files
                            modifyCurrentlyHandlingFiles(0, filename, j);

                            ArrayList<byte[]> fileBytesToBeHandled = currentlyHandlingQueue.get(filename);

                            // add all bytes
                            for (int k = 0; i < fileBytesToBeHandled.size(); k++){

                                Message message = new Message(StatusCode.REQUEST, fileBytesToBeHandled.get(k));
                                consumerThreadsListInfo.get(j).modifyBuffer(0, message);
                            }

                            modifyCurrentlyHandlingQueue(1, filename, null);
                            modifyCurrentlyHandlingQueueOrder(1, null, i);
                            i--;
                            break;
                        }


                    }
                }

            }
            System.out.println("Finished receiving all files. Shutting down.");
            listenerThread.join();


        } catch (Exception e) {
            e.printStackTrace();
        }


    }


}

