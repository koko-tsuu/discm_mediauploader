import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.TimeUnit;

// Singleton
public class Producer {

    static class ListenerThread implements Runnable
    {
        // Overriding the run Method
        @Override
        public void run()
        {
            while (!producerThreads.isEmpty()) {
                try {
                    Message messageFromConsumer = (Message) objectInputStream.readObject();
                   if (messageFromConsumer.getStatusCode() == StatusCode.QUEUE_FULL) {
                        System.out.println("[Warning] Queue is full. File [" + messageFromConsumer.getFilename() + "] failed to transfer.");
                    }
                }catch (Exception e) {
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
    static ObjectOutputStream objectOutputStream;
    static ObjectInputStream objectInputStream;
    static ArrayList<ProducerThread> producerThreads = new ArrayList<>();

    static Thread listenerThread;
    static Socket socket;


    static void mainThread(int numThreads)
    {
        ProducerThread.setObjectStream(objectOutputStream);
        for (int i = 0; i < numThreads; i++) {
            producerThreads.add(new ProducerThread(i));
        }

        listenerThread = new Thread(new ListenerThread());
        listenerThread.start();

        while (!producerThreads.isEmpty()) {
            for (int i = 0; i < producerThreads.size(); i++) {
                if (producerThreads.get(i).getIsDone())
                {
                    producerThreads.remove(i);
                    i--;
                }
            }
        }

        boolean consumerHasReceivedCompletion = false;
        while (!consumerHasReceivedCompletion) {
            try {
                socket.setSoTimeout(10000);

                Message producerMessage = new Message(StatusCode.FILE_ALL_COMPLETE);
                objectOutputStream.writeObject(producerMessage);
                objectOutputStream.flush();

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
        //System.out.print("Number of producers: ");
       // Scanner scanner = new Scanner(System.in);

        int producerInstances = 1; // scanner.nextInt();

       // scanner.close();



        // 1: create a socket to connect to


        try {
            ServerSocket serverSocket = new ServerSocket(3000);

            System.out.println("Hosting server: " + serverSocket.getInetAddress().getHostAddress());
            socket = serverSocket.accept();


            ///

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
