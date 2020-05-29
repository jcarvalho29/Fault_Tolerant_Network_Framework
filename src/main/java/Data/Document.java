package Data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class Document {

    private String Root;

    private String documentName;

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

    public Document(String Root, String hash, int numberOfChunks, String DocumentName, String hashAlgorithm){
        this.Root = Root;
        this.documentName = DocumentName;

        this.cm = new ChunkManager(this.Root, hash, hashAlgorithm, numberOfChunks);

    }

    /*
     * Opens the Data.Document, divides it into Byte[] with the size of the specified datagramMaxSize, calls createChunks to create chunks,
     *  and then writes them into a tmp Folder within the Node Folder
     * The reading process only loads a maximum of 10k Byte[] of size datagramMaxSize at a time, creating a maximum of 10k chunks at a time.
     * This reduces the amount of RAM needed
     */
    private void loadDocument(int maxDatagramSize, String hashAlgorithm){

        try {
            File fileToLoad = new java.io.File(this.localDocumentPath);
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
     * Given an ArrayList of chunks, this function uses the Data.ChunkManager object to write them to Root/MacAddress/hash/chunks folder
     * */
    public void addChunks(ArrayList<Chunk> chunks){
        this.cm.addChunks(chunks);
    }

    /*
     * Uses the Data.ChunkManager object to delete all the saved chunks in Root/MacAddress/hash/chunks as well as the MetaInfo File and the hash Folder
     * */
    public void delete(){
        this.cm.eraseChunks();
        File hashDocument = new File(this.Root + "/" + this.cm.getHash());

        while(hashDocument.exists() && !hashDocument.delete());
    }

    /*
     * Reads all chunks that compose a Data.Document and recreates the original Data.Document in a destination Folder within the Node Folder
     *   String folder => Destination Folder within the Node Folder
     */
    public void writeDocumentToFolder(String folder){
        System.out.println("ROOT => " + this.Root);
        System.out.println("FOLDER => " + folder);
        System.out.println("DOCNAME => " + this.documentName);
        String folderToWritePath = folderPathNormalizer(folder) + this.documentName ;
        String folderToReadPath = folderPathNormalizer(this.Root + "/" + this.cm.getHash()) + "/Chunks/";

        Chunk chunk;
        int i = 0;
        int numberOfChunks = this.cm.getNumberOfChunks();
        try {
            Path p;
            FileOutputStream outputStream = new FileOutputStream(folderToWritePath, true);

            while (i < numberOfChunks) {
                p = Paths.get(folderToReadPath + (i + Integer.MIN_VALUE) + ".chunk");
                //chunk = new Data.Chunk(Files.readAllBytes(p), (i + Integer.MIN_VALUE));

                //outputStream.write(chunk.getChunk());
                outputStream.write(Files.readAllBytes(p));
                i++;
            }
            outputStream.close();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public byte[] getDocumentAsByteArray(){
        return this.cm.getInfoInByteArray();
    }

    public ChunkManager getDocumentChunkManager(){
        return this.cm;
    }
    /*
    * Retrieves the DocumentName
    * */
    public String getDocumentName(){
        return this.documentName;
    }

    /*
    * Retrieves the Data.Document's Hash
    * */
    public String getHash(){
        return this.cm.getHash();
    }

    /*
    * Checks if all the chunks that compose the Data.Document are in Memory
    * */
    public boolean isFull(){
        return this.cm.getFull();
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
