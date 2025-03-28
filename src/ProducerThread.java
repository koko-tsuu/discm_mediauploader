import java.io.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;

public class ProducerThread {

    class PThread implements Runnable {
        @Override
        public void run()
        {
            try {
                int fileIndex = 0;
                File folder = new File(System.getProperty("user.dir") + "\\" + path);
                File[] listOfFiles = folder.listFiles();


                System.out.println("Current working directory in Java : " + folder);

                if (listOfFiles != null) {
                    // while all files have not been uploaded
                    while (fileIndex != listOfFiles.length) {
                        String fileDuplicateName = checkIfDuplicate(listOfFiles[fileIndex]);
                        // not a duplicate, therefore null
                        if (fileDuplicateName == null) {
                            System.out.println("Sending file: " + listOfFiles[fileIndex].getName());
                            byte[] buffer = new byte[MAX_BYTES];
                            //try-with-resources to ensure closing stream
                            try (FileInputStream fis = new FileInputStream(listOfFiles[fileIndex]);

                                 BufferedInputStream bis = new BufferedInputStream(fis)) {

                                int bytesAmount = 0;
                                while ((bytesAmount = bis.read(buffer)) > 0) {
                                    send(StatusCode.REQUEST, bytesAmount, listOfFiles[fileIndex].getName(), Arrays.copyOf(buffer, bytesAmount));
                                }

                                send(StatusCode.FILE_COMPLETE, listOfFiles[fileIndex].length(), listOfFiles[fileIndex].getName(), null);
                            }



                        }
                        //
                        else {
                            System.out.println("[Duplicate] " + listOfFiles[fileIndex].getName() + " <->" + fileDuplicateName);
                        }
                        fileIndex++;
                 }


                    isDone = true;

                } else {
                    System.out.println("No files to upload.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }


        }
    }

    private static ObjectOutputStream objectOutputStream;

    private final int threadIndex;
    private boolean isDone = false;
    private final String path;
    private int MAX_BYTES = 1024 * 3;
    private Thread producerThread;
    private volatile static Dictionary<String, String> allVideosHash = new Hashtable<>();
    static MessageDigest md;

    static {
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {

        }
    }

    public ProducerThread(int threadIndex) {

        this.threadIndex = threadIndex;
        this.path = String.valueOf(this.threadIndex + 1);
        this.producerThread = new Thread(new PThread());
        this.producerThread.start();
    }


    static void setObjectStream(ObjectOutputStream objectOutputStream)
    {
        ProducerThread.objectOutputStream = objectOutputStream;

    }


    synchronized static String checkIfDuplicate(File file)
    {
        try {
            InputStream inputStream = new FileInputStream(file);

            DigestInputStream digestInputStream = new DigestInputStream(inputStream, md);
            byte[] digest = digestInputStream.getMessageDigest().digest();

            String hashString = bytesToHex(digest);
            String duplicateFile = allVideosHash.get(hashString);

            // not a duplicate
            if (duplicateFile == null) {
                allVideosHash.put(hashString, file.getName());
                System.out.println(allVideosHash);
            }

            // is a duplicate
            else {
                return duplicateFile;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;

    }

    synchronized static void send(StatusCode statusCode, long byteSize, String filename, byte[] data) {
        try {
            if (statusCode == StatusCode.REQUEST) {
                objectOutputStream.writeObject(new Message(byteSize, StatusCode.REQUEST, filename, data));

            }
            else if (statusCode == StatusCode.FILE_COMPLETE) {
                objectOutputStream.writeObject(new Message(byteSize, StatusCode.FILE_COMPLETE, filename));
            }

            objectOutputStream.flush();
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }





    boolean getIsDone(){
        return isDone;
    }

}
