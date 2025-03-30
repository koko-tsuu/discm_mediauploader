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

                // No files found
                if (listOfFiles != null) {

                    // while all files have not been uploaded
                    while (fileIndex != listOfFiles.length) {

                        String fileDuplicateName = checkIfDuplicate(listOfFiles[fileIndex]);

                        // not a duplicate file, therefore null
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
                        // duplicate found, this will no longer upload the file
                        else {
                            System.out.println("[Duplicate] " + listOfFiles[fileIndex].getName() + " <-> " + fileDuplicateName);
                        }
                        fileIndex++;
                 }
                    isDone = true; // this is for Producer.java

                } else {
                    System.out.println("[Empty Folder] No files to upload: " + folder.getName());
                    isDone = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }


        }
    }

    // this is static so that there is only one ObjectOutputStream
    private static ObjectOutputStream objectOutputStream;

    private final int threadIndex;              // unique identifier
    private boolean isDone = false;             // producerThread has finished uploading all the files in its folder
    private final String path;                  // path to folder
    private int MAX_BYTES = 1024 * 3;           // feel free to change this
    private Thread producerThread;              // producerThread holder
    private volatile static Dictionary<String, String> allVideosHash = new Hashtable<>();       // to check for duplicates


    public ProducerThread(int threadIndex) {

        this.threadIndex = threadIndex;
        this.path = String.valueOf(this.threadIndex + 1); // always start at 0 so i'm just incrementing this
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
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            InputStream inputStream = new FileInputStream(file);
            byte[] buffer = new byte[1024];

            System.out.println("Checking duplicate: " + file.getName());

            // Read file to get its checksum
            int numRead;
            do {

                numRead = inputStream.read(buffer);
                if (numRead > 0) {
                    md.update(buffer, 0, numRead);
                }
            } while (numRead != -1);
            byte[] digest = md.digest();

            // This is required
            String hashString = bytesToHex(digest);

            String duplicateFile = allVideosHash.get(hashString);

            // not a duplicate
            if (duplicateFile == null) {
                allVideosHash.put(hashString, file.getName());
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


    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    // Only one message can be sent at a time, so messages don't get jumbled up
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


    boolean getIsDone(){
        return isDone;
    }

}
