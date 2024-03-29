package Data;

import Messages.ChunkMessage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class Document {

    private String Root;

    public String documentName;

    public ChunkManager cm;

    /*
    * Path to a local Data.Document that will be processed
    * */
    private String localDocumentPath;

    public Document(String Root, String documentName, ChunkManager cm){
        this.Root = Root;
        this.documentName = documentName;
        this.cm = cm;
    }
    public Document(String Root, String localDocumentPath, String DocumentName, int maxDatagramSize, String hashAlgorithm){
        this.Root = Root;
        this.localDocumentPath = localDocumentPath;
        this.documentName = DocumentName;

        loadDocument(maxDatagramSize, hashAlgorithm);
    }

    public Document(String Root, String localDocumentPath, String DocumentName, int maxDatagramSize, String hashAlgorithm, int maxChunksLoadedAtaTime){
        this.Root = Root;
        this.localDocumentPath = localDocumentPath;
        this.documentName = DocumentName;

        loadDocument(maxDatagramSize, hashAlgorithm, maxChunksLoadedAtaTime);
    }

    public Document(String Root, String hash, int datagramMaxSize, int numberOfChunks, String DocumentName, String hashAlgorithm){
        this.Root = Root;
        this.documentName = DocumentName;

        this.cm = new ChunkManager(this.Root, datagramMaxSize, hash, hashAlgorithm, numberOfChunks);

    }

    /*
     * Opens the Data.Document, divides it into Byte[] with the size of the specified datagramMaxSize, calls createChunks to create chunks,
     *  and then writes them into a tmp Folder within the Node Folder
     * The reading process only loads a maximum of 10k Byte[] of size datagramMaxSize at a time, creating a maximum of 10k chunks at a time.
     * This reduces the amount of RAM needed
     */
    private void loadDocument(int maxDatagramSize, String hashAlgorithm){

        try {
            File fileToLoad = new File(this.localDocumentPath);
            FileInputStream fis = new FileInputStream(this.localDocumentPath);
            byte[] info = new byte[Math.toIntExact(fileToLoad.length())];
            fis.read(info);
            this.cm = new ChunkManager(this.Root, info, maxDatagramSize, hashAlgorithm);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadDocument(int datagramMaxSize, String hashAlgorithm, int maxChunksLoadedAtaTime){
        this.cm = new ChunkManager(this.Root, this.localDocumentPath, hashAlgorithm, datagramMaxSize,  maxChunksLoadedAtaTime);
    }

    /*
     * Given an ArrayList of chunks, this function uses the Data.ChunkManager object to write them to Root/nodeIdentifier/hash/chunks folder
     * */
    public boolean addChunks(ChunkMessage[] chunks){
        return this.cm.addChunks(chunks);
    }

    /*
     * Uses the Data.ChunkManager object to delete all the saved chunks in Root/nodeIdentifier/hash/chunks as well as the MetaInfo File and the hash Folder
     * */
    public void delete(){
        this.cm.eraseChunks();
        File hashDocument = new File(this.Root + "/" + this.cm.mi.Hash);

        while(hashDocument.exists() && !hashDocument.delete());
    }

    /*
     * Reads all chunks that compose a Data.Document and recreates the original Data.Document in a destination Folder within the Node Folder
     *   String folder => Destination Folder within the Node Folder
     */
    public void writeDocumentToFolder(String folder){
        if(this.cm.isWrittenToMemory) {
            System.out.println("ROOT => " + this.Root);
            System.out.println("FOLDER => " + folder);
            System.out.println("DOCNAME => " + this.documentName);

            System.out.println("");
            this.cm.mi.print();
            System.out.println("");

            String folderToWritePath = folderPathNormalizer(folder) + this.documentName;
            String folderToReadPath = folderPathNormalizer(this.Root + "/" + this.cm.mi.Hash) + "/Chunks/";

            int i = 0;
            int j;
            int readBytes = this.cm.mi.datagramMaxSize;
            int totalReadBytes;
            int numberOfChunks = this.cm.mi.numberOfChunks;
            try {
                Path p;
                FileOutputStream outputStream = new FileOutputStream(folderToWritePath, false);

                long start = System.currentTimeMillis();

                int chunksInBuffer = Math.min(10000, numberOfChunks);
                byte[] buffer = new byte[this.cm.mi.datagramMaxSize * chunksInBuffer];
                while (i < numberOfChunks) {
                    j = 0;
                    totalReadBytes = 0;
                    while (j < chunksInBuffer && i < numberOfChunks) {
                        if (i == numberOfChunks - 1)
                            readBytes = (int) (this.cm.mi.chunksSize - ((numberOfChunks - 1) * this.cm.mi.datagramMaxSize));

                        p = Paths.get(folderToReadPath + (i + Integer.MIN_VALUE) + ".chunk");

                        System.arraycopy(Files.readAllBytes(p), 0, buffer, totalReadBytes, readBytes);
                        totalReadBytes += readBytes;
                        i++;
                        j++;
                    }
                    outputStream.write(Arrays.copyOf(buffer, totalReadBytes));
                }
                buffer = null;
                outputStream.close();
                long end = System.currentTimeMillis();
                System.out.println("TOOK " + (end - start));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else
            System.out.println("Document isn't written to memory yet");
    }

    public byte[] getDocumentAsByteArray(){
        return this.cm.getInfoInByteArray();
    }

    /*
     * Normalizes the given path
     * */
    private String folderPathNormalizer(String path) {

        path = path + '/';
        int charIndex;

        while ((charIndex = path.indexOf("//")) != -1)
            path = path.substring(0, charIndex) + path.substring(charIndex + 1);


        return path;
    }
}
