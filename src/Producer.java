import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.TimeUnit;

/*
      NOTE: Format for folder to store the videos to upload is "1" "2" "3", so on and so forth (remove the quotations)
 */

// Singleton
public class Producer {

    static class ListenerThread implements Runnable
    {
        // Overriding the run Method
        @Override
        public void run()
        {
            // While all threads are still busy
            while (!producerThreads.isEmpty()) {

                // This is for reading if queue is full
                try {
                    Message messageFromConsumer = (Message) objectInputStream.readObject();
                   if (messageFromConsumer.getStatusCode() == StatusCode.QUEUE_FULL) {
                        System.out.println("[Warning] Queue is full. File [" + messageFromConsumer.getFilename() + "] failed to transfer.");
                    }
                }catch (Exception e) {

                    // Not a graceful way to terminate the socket, but it works
                    System.out.println("Consumer may have disconnected. Closing socket.");
                    try {
                        socket.close();
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
    }

    // OutputStream is shared to its threads
    static ObjectOutputStream objectOutputStream;
    static ObjectInputStream objectInputStream;

    static ArrayList<ProducerThread> producerThreads = new ArrayList<>();

    static Thread listenerThread;
    static Socket socket;


    static void mainThread(int numThreads)
    {
        // Thread setup
        ProducerThread.setObjectStream(objectOutputStream); // this is to use the same objectOutputStream for all producerThreads
        for (int i = 0; i < numThreads; i++) {
            producerThreads.add(new ProducerThread(i));
        }


        // Queue full listener
        listenerThread = new Thread(new ListenerThread());
        listenerThread.start();

        //
        while (!producerThreads.isEmpty()) {
            for (int i = 0; i < producerThreads.size(); i++) {
                if (producerThreads.get(i).getIsDone())
                {
                    producerThreads.remove(i);
                    i--;
                }
            }
        }

        // There's also this thing, but getting the response from the consumer that it's finished with downloading all files
        // doesn't really work
        boolean consumerHasReceivedCompletion = false;
        while (!consumerHasReceivedCompletion) {
            try {
                socket.setSoTimeout(10000);

                Message producerMessage = new Message(StatusCode.FILE_ALL_COMPLETE);
                objectOutputStream.writeObject(producerMessage);
                objectOutputStream.flush();

                // This part doesn't really happen, I think
                objectInputStream.readObject();
                Message consumerMessage = (Message) objectInputStream.readObject();
                if (consumerMessage.getStatusCode() == StatusCode.FILE_ALL_COMPLETE) {
                    consumerHasReceivedCompletion = true;
                }


            } catch (Exception e) {
                // assume consumer has received it
                consumerHasReceivedCompletion = true;

            }
        }

    }


    public static void main(String[] args) {
        System.out.print("Number of producers: ");
        Scanner scanner = new Scanner(System.in);

        int producerInstances = scanner.nextInt();;

        scanner.close();



        // 1: create a socket to connect to
        try {
            ServerSocket serverSocket = new ServerSocket(3000);

            System.out.println("Hosting server: " + serverSocket.getInetAddress().getHostAddress());
            socket = serverSocket.accept();


            try {
                System.out.println("Client has connected.");
                objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                objectInputStream = new ObjectInputStream(socket.getInputStream());


                mainThread(producerInstances);


                socket.close();

                objectOutputStream.close();
                objectInputStream.close();



            } catch (Exception e) {
            }
        } catch (Exception e) {
        }


    }
}
