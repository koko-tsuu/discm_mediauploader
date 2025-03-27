import java.io.*;
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
                    if (messageFromConsumer == null) {}
                }catch (Exception e) {
                    e.printStackTrace();
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
                e.printStackTrace();
            }
        }

    }


    public static void main(String[] args) {
        boolean isConnected = false;
        //System.out.print("Number of producers: ");
       // Scanner scanner = new Scanner(System.in);

        int producerInstances = 3; // scanner.nextInt();

       // scanner.close();

        listenerThread = new Thread(new ListenerThread());
        listenerThread.start();

        // 1: create a socket to connect to

        while (!isConnected) {
            try {
                socket = new Socket("localhost", 3000);
                System.out.println("Connected to server");
                isConnected = true;
                objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                objectInputStream = new ObjectInputStream(socket.getInputStream());


                mainThread(producerInstances);


                socket.close();

                objectOutputStream.close();
                objectInputStream.close();

            } catch (IOException e) {
                System.out.println("Could not connect to server. Retrying in 3 seconds.");
                try {
                    TimeUnit.SECONDS.sleep(3);
                } catch (InterruptedException ex) {
                }
            }
        }


    }
}
