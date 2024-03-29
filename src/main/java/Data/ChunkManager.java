package Data;

import java.io.File;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

import Messages.ChunkMessage;
import org.apache.commons.io.FileUtils;


public class ChunkManager {

    /*
    * These 3 parameters are used to construct a path where the Filechunks of the Information can be found
    */
    private String Root;

    public ChunkManagerMetaInfo mi;
    private ReentrantLock mi_Lock;
    private ReentrantLock missingChunks_Lock;


    private ReentrantLock memoryWriter_Lock;
    private boolean isThreadWriting;
    private ArrayList<ChunkMessage[]> chunkMessagesToWrite;
    public boolean isWrittenToMemory;
    private long numberOfWrittenChunks;

    /*
    * Creates a basic ChunkManager object that is used to initialize the class from a DocumentMetaInfo file
    * */
    public ChunkManager(String Root, String hash){
        Root = folderPathNormalizer(Root);
        this.Root = Root;
        this.mi_Lock = new ReentrantLock();
        this.missingChunks_Lock = new ReentrantLock();
        readDocumentMetaInfoFromFile(hash);

        this.isWrittenToMemory = this.mi.full;
        if(!this.isWrittenToMemory){
            this.memoryWriter_Lock = new ReentrantLock();
            this.isThreadWriting = false;
            this.chunkMessagesToWrite = new ArrayList<ChunkMessage[]>();
            this.numberOfWrittenChunks = this.mi.numberOfChunksInArray; // PODE HAVER PROBLEMAS QUANDO O RECEPTOR VAI ABAIXO ENQUANTO A TRANSFERENCIA ESTA A DECORRER
        }

    }

    /*
    * Creates a ChunkManager Object for a File to be received and written to Memory
    *   int numberOfChunks => Number of Chunks this File is composed of
    *   String mac => MAC address of the Node who's sending the File
    *   String name => Name of the File
    *   String hash => Hash of the File
    */

    public ChunkManager (String root, int datagramMaxSize, String hash, String hashAlgorithm, int numberOfChunks){
        root = folderPathNormalizer(root);

        String hashFolderPath = root + hash + "/";
        File hashFolder = new File(hashFolderPath);

        while((!hashFolder.exists() && !hashFolder.isDirectory()) && !hashFolder.mkdir());

        String chunksFolderPath = hashFolderPath + "Chunks/";
        File chunksFolder = new File(chunksFolderPath);

        while((!chunksFolder.exists() && !chunksFolder.isDirectory()) && !chunksFolder.mkdir());

        this.Root = root;

        this.mi = new ChunkManagerMetaInfo();
        this.mi_Lock = new ReentrantLock();

        this.mi.datagramMaxSize = datagramMaxSize;
        this.mi.numberOfChunks = numberOfChunks;
        this.mi.numberOfChunksInArray = 0;
        this.mi.chunksSize = 0;
        this.mi.full = false;
        this.mi.Hash = hash;
        this.mi.HashAlgorithm = hashAlgorithm;

        this.missingChunks_Lock = new ReentrantLock();

        this.mi.missingChunks = new boolean[numberOfChunks];
        this.mi.missingChunks[0] = true;
        for (int i = 1; i < numberOfChunks; i += i) {
            System.arraycopy(this.mi.missingChunks, 0, this.mi.missingChunks, i, Math.min((numberOfChunks - i), i));
        }

        this.memoryWriter_Lock = new ReentrantLock();
        this.isThreadWriting = false;
        this.chunkMessagesToWrite = new ArrayList<ChunkMessage[]>();
        this.isWrittenToMemory = false;
        this.numberOfWrittenChunks = 0;


        writeDocumentMetaInfoToFile();
    }

    /*
     * Creates a Data.ChunkManager Object for Info to be saved to Memory
     *   String root => Root path to the file scope of the program
     *   String mac => MAC address of the Node who's sending the File
     *   byte[] info => bytes of the information to be saved
     *   int datagramMaxSize => Maximum size a single datagram can take
     */

    public ChunkManager (String root, byte[] info, int datagramMaxSize, String hashAlgorithm) {
        root = folderPathNormalizer(root);

        this.Root = root;

        this.mi = new ChunkManagerMetaInfo();
        this.mi_Lock = new ReentrantLock();


        this.mi.datagramMaxSize = datagramMaxSize;
        this.mi.numberOfChunks = (int) Math.ceil((double) info.length / (double) this.mi.datagramMaxSize);

        this.mi.numberOfChunksInArray = 0;
        this.mi.chunksSize = info.length;

        Chunk[] chunks = createChunks(splitInfoIntoArrays(info), this.mi.numberOfChunks, this.mi.numberOfChunks);

        this.mi.HashAlgorithm = hashAlgorithm;
        this.mi.Hash = hash_Chunks(chunks, hashAlgorithm);

        File hashFolder = new File(this.Root + "/" + this.mi.Hash + "/");
        while (!hashFolder.exists() && !hashFolder.isDirectory() && !hashFolder.mkdir()) ;

        this.mi.full = false;
        int writtenChunks = 0;

        while (!this.mi.full){
            writtenChunks += writeChunksToFolder(chunks);

            if(writtenChunks == this.mi.numberOfChunks);
            this.mi.full = true;
        }
        this.isWrittenToMemory = true;

        writeDocumentMetaInfoToFile();
    }

    /*
    * Constructor that creates a ChunkManager object. This Constructor will load a local file to
    * chunks and only have maxChunksLoadedAtaTime chunks in Ram at a time
    * */
    public ChunkManager (String root, String localFilePath, String hashAlgorithm, int datagramMaxSize, int maxChunksLoadedAtaTime) {
        root = folderPathNormalizer(root);

        this.Root = root;

        this.mi = new ChunkManagerMetaInfo();
        this.mi.Hash = "TMPFILE";
        this.mi_Lock = new ReentrantLock();
        this.mi.HashAlgorithm = hashAlgorithm;

        File tempFolder = new File(this.Root + "/" + this.mi.Hash + "/");
        while (!tempFolder.exists() && !tempFolder.isDirectory() && !tempFolder.mkdir()) ;

        this.mi.datagramMaxSize = datagramMaxSize;

        File f = new File(localFilePath);
        this.mi.chunksSize = f.length();
        this.mi.numberOfChunks = (int) Math.ceil((double) this.mi.chunksSize / (double) this.mi.datagramMaxSize);

        this.mi.numberOfChunksInArray = 0;

        String oldpath = this.Root + "/" + this.mi.Hash + "/Chunks/";
        this.mi.Hash = loadInfoRamEfficient(localFilePath, maxChunksLoadedAtaTime);
        String newpath = this.Root + "/" + this.mi.Hash + "/Chunks/";

        File hashFolder = new File(this.Root + "/" + this.mi.Hash + "/");
        while (!hashFolder.exists() && !hashFolder.isDirectory() && !hashFolder.mkdir()) ;
        hashFolder = new File(this.Root + "/" + this.mi.Hash + "/Chunks/");
        while (!hashFolder.exists() && !hashFolder.isDirectory() && !hashFolder.mkdir()) ;


        File mover;
        File deleter;
        for(int i = 0; i < this.mi.numberOfChunks; i++){
            mover = new File(oldpath + (i + Integer.MIN_VALUE) + ".chunk");
            deleter = new File(oldpath + (i + Integer.MIN_VALUE) + ".chunk");
            mover.renameTo(new File(newpath + (i + Integer.MIN_VALUE) + ".chunk"));
            deleter.delete();
        }
        this.mi.full = true;
        this.isWrittenToMemory = true;

        File oldChunksFolder = new File(oldpath);
        oldChunksFolder.delete();
        tempFolder.delete();

        writeDocumentMetaInfoToFile();
    }

    /*
    * Writes the Data.DocumentMetaInfo to a file within the root/mac/hash path
    */
    public void writeDocumentMetaInfoToFile(){
        String FileInfoPath = this.Root + this.mi.Hash + "/";
        String documentMetaInfoFilePath = FileInfoPath + "ChunkManagerMeta.info";

        File FileInfo = new File(FileInfoPath);

        while((!FileInfo.exists() && !FileInfo.isDirectory()) && !FileInfo.mkdir());

        FileOutputStream fileOut = null;
        try {
            fileOut = new FileOutputStream(documentMetaInfoFilePath);
            ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
            this.mi_Lock.lock();
            ChunkManagerMetaInfo miCopy= new ChunkManagerMetaInfo(this.mi);
            this.mi_Lock.unlock();
            objectOut.writeObject(miCopy);
            objectOut.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
    * Reads the meta info from root/mac/hash and initializes the MetaInfo object
    */
    public void readDocumentMetaInfoFromFile(String hash){
        String FileInfoPath = this.Root + hash + "/";
        String documentMetaInfoFilePath = FileInfoPath + "ChunkManagerMeta.info";

        File FileInfo = new File(FileInfoPath);

        if(FileInfo.exists()) {
            FileInputStream fileIn = null;
            try {
                fileIn = new FileInputStream(documentMetaInfoFilePath);
                ObjectInputStream objectIn = new ObjectInputStream(fileIn);

                Object obj = objectIn.readObject();

                this.mi = (ChunkManagerMetaInfo) obj;

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    /*
    * Deletes the current Meta Info File in root/mac/hash and writes the new Meta Info to the same path
    */
    public void updateDocumentMetaInfoFile(){
        String FileInfoPath = this.Root + this.mi.Hash + "/";
        String documentMetaInfoFilePath = FileInfoPath + "ChunkManagerMeta.info";

        File FileInfo = new File(FileInfoPath);

        if(FileInfo.exists()) {
            File documentMetaInfo = new File(documentMetaInfoFilePath);
            while(!documentMetaInfo.delete());

            writeDocumentMetaInfoToFile();
        }
    }

    /*
    * This will load a File to Chunks to the Memory and will only have maxChunksLoadedAtaTime chunks in Ram at a time
    * */
    public String loadInfoRamEfficient(String localFilePath, int maxChunksLoadedAtaTime){
        Hash h = new Hash(this.mi.HashAlgorithm);
        try {
            FileInputStream fis = new FileInputStream(localFilePath);
            BufferedInputStream bis = new BufferedInputStream(fis, 64*1024);
            int i = 0;
            int read = 1;
            long readlimit;
            byte[][] info = new byte[Math.min(maxChunksLoadedAtaTime, this.mi.numberOfChunks)][];
            byte[] buffer;
            Chunk[] Chunks;

            int j;
            while(i < this.mi.numberOfChunks){
                j = 0;
                for(; j < maxChunksLoadedAtaTime && i < this.mi.numberOfChunks && read != 0; i++, j++){
                    if(i == this.mi.numberOfChunks-1)
                        readlimit = this.mi.chunksSize - this.mi.datagramMaxSize * i;
                    else
                        readlimit = this.mi.datagramMaxSize;
                    buffer = new byte[(int) readlimit];
                    read = bis.read(buffer);
                    h.updateHash(buffer);
                    info[j] = buffer;
                }
                Chunks = createChunks(info, j, i);
                writeChunksToFolder(Chunks);
            }
            bis.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return h.extractHash();
    }

    /*
    * This will split a byte[] into multiple byte[] based on the mi.datagramMaxSize
    * Returns a ArrayList with the corresponding byte[]
    * */
    private byte[][] splitInfoIntoArrays(byte[] info){
        byte[][] splits = new byte[this.mi.numberOfChunks][];

        int i = 0;
        long readlimit;

        while(i < this.mi.numberOfChunks) {
            if(i == this.mi.numberOfChunks-1)
                readlimit = (this.mi.datagramMaxSize * i) + this.mi.chunksSize - this.mi.datagramMaxSize*i;
            else
                readlimit = this.mi.datagramMaxSize * (i + 1);

            //System.out.println("READ FROM " + this.mi.datagramMaxSize * i + " TO " + readlimit + "( " + this.mi.chunksSize +  " )");
            splits[i] = Arrays.copyOfRange(info, this.mi.datagramMaxSize * i, (int) readlimit);
            i++;
        }

        return splits;
    }

    /*
    * Writes a given number of FileChunks to a specified Folder within the MAC Folder
    *   FileChunk[] fcs => Array of FileChunks to be written
    *   int size => Size of the FileChunk Array
    */
    private int writeChunksToFolder(Chunk[] chunks){
        int i;
        int size = chunks.length;

        String folderPath = this.Root + this.mi.Hash + "/Chunks/";
        File ficheiro = new File(folderPath);
        File filePointer;
        String path;
        int writtenChunks = 0;

        while((!ficheiro.exists() && !ficheiro.isDirectory()) && !ficheiro.mkdir());

        Path file;
        for(i = 0; i < size; i++){
            try {
                Chunk fc = chunks[i];
                path = folderPath + fc.place + ".chunk";
                filePointer = new File(path);

                //SO VAI ESCREVER O FICHEIRO SE ELE NAO EXISTIR
                if(!filePointer.exists()) {
                    file = Paths.get(path);
                    Files.write(file, fc.Chunk);
                    writtenChunks++;
                }
                else
                    System.out.println("REPEATED CHUNK" + fc.place);
            }
                catch (Exception e) {
                e.printStackTrace();
            }
        }

        return writtenChunks;
    }

    /*
     * Sets the local Meta Info equal to a received Meta Info
     * */
    public void setDocumentMetaInfo(ChunkManagerMetaInfo dmi){
        this.mi = dmi;
    }

    /*
    * This will read ALL the chunks, aggregate them into a byte[] and return said byte[]
    * */
    public byte[] getInfoInByteArray(){
        String folderToReadPath = this.Root + this.mi.Hash + "/Chunks/";

        int i = 0;
        int numberOfChunks = this.mi.numberOfChunks;

        byte[] info = null;
        try {
            Path p;
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();


            byte c[] = outputStream.toByteArray( );
            while (i < numberOfChunks) {
                p = Paths.get(folderToReadPath + (i + Integer.MIN_VALUE) + ".chunk");

                outputStream.write(Files.readAllBytes(p));
                i++;
            }
            info = new byte[Math.toIntExact(this.mi.chunksSize)];
            info = outputStream.toByteArray();
            outputStream.close();
        }
        catch (Exception e){
            e.printStackTrace();
        }

        return info;
    }

   /* private void metaInfoProcessor(ArrayList<Chunk> chunks){
        new Thread(() -> {

            ArrayList<Integer> addedChunksIds = new ArrayList<Integer>();
            int numberOfChunksAdded = 0;
            long sizeOfAddedChunks = 0;

            for (Chunk c : chunks) {
                //System.out.println("ID!!! => " + fc.getPlace());

                    addedChunksIds.add(c.place);
                    numberOfChunksAdded++;
                    sizeOfAddedChunks += c.Chunk.length;

            }
            this.mi_Lock.lock();

            this.mi.missingChunks.removeAll(addedChunksIds);
            this.mi.numberOfChunksInArray += numberOfChunksAdded;
            this.mi.chunksSize += sizeOfAddedChunks;

            if (this.mi.numberOfChunksInArray == this.mi.numberOfChunks) {
                this.mi.full = true;
            }
            this.mi_Lock.unlock();

            updateDocumentMetaInfoFile();
        }).start();

    }*/

    /*
    * Updates what Filechunks have been received, which ones are missing, what's the current size of the saved Filechunks,
    * calls writeFileChunksToFolder to write the new Filechunks, and updates the MetaInfo File
    *   ArrayList<FileChunk> fcs => Newly received Filechunks to be written
    */
    public boolean addChunks (ChunkMessage[] chunkMessages){


        /*if(numberOfMissingChunks > chunk_size) {


            new Thread(() -> {
                //long startTime = System.currentTimeMillis();

                boolean[] missingc = new boolean[this.mi.numberOfChunks];
                missingc[0] = true;

                for (int i = 1; i < this.mi.numberOfChunks; i += i) {
                    System.arraycopy(missingc, 0, missingc, i, ((this.mi.numberOfChunks - i) < i) ? (this.mi.numberOfChunks - i) : i);
                }

                for (Chunk c : chunksCopy) {
                    this.mi_Lock.lock();

                    missingc[c.place - Integer.MIN_VALUE] = false;
                    this.mi.numberOfChunksInArray++;
                    this.mi.chunksSize += c.Chunk.length;

                    this.mi_Lock.unlock();
                }

                this.missingChunks_Lock.lock();
                for(int i = 0; i < this.mi.numberOfChunks; i++)
                    this.mi.missingChunks[i] = this.mi.missingChunks[i] & missingc[i];
                this.missingChunks_Lock.unlock();

                //long endTime = System.currentTimeMillis();

                //System.out.println("SPENT " + (endTime - startTime) + " ms UPDATING THE METAINFO (" + chunks.size() + "Chunks )");
                this.mi_Lock.lock();
                if (this.mi.numberOfChunksInArray == this.mi.numberOfChunks) {
                    this.mi.full = true;
                }
                this.mi_Lock.unlock();

                updateDocumentMetaInfoFile();
            }).start();

        }
        else{*/
        //NECESSÁRIO PARA MULTITHREAD
        /*
         int totalNumberOfChunks = this.mi.numberOfChunks;
         boolean[] missingc = new boolean[totalNumberOfChunks];

        //fill with true
        missingc[0] = true;
        for (int i = 1; i < totalNumberOfChunks; i += i)
            System.arraycopy(missingc, 0, missingc, i, Math.min((totalNumberOfChunks - i), i));
*/
        this.mi_Lock.lock();
        int i = 0;
        int id;
        try {
            while(i < chunkMessages.length) {
                //missingc[chunkMessages[i].chunk.place - Integer.MIN_VALUE] = false;
                id = chunkMessages[i].chunk.place - Integer.MIN_VALUE;
                if(this.mi.missingChunks[id]) {
                    this.mi.missingChunks[id] = false;
                    this.mi.numberOfChunksInArray++;
                    this.mi.chunksSize += chunkMessages[i].chunk.Chunk.length;
                }
                else
                    System.out.println("REPEATED CHUNK NO ADDCHUNKS");
                i++;
            }
        }
        catch (Exception e){
            e.printStackTrace();
            if(chunkMessages != null) {
                System.out.println("ChunkMessages length " + chunkMessages.length + " I " + i);
                if(chunkMessages[i] != null)
                    System.out.println("Chunk place " + (chunkMessages[i].chunk.place - Integer.MIN_VALUE) + " MissingChunks length " + this.mi.missingChunks.length);
            }
            else
                System.out.println("CHUNK MESSAGES NULL");
        }
        this.mi_Lock.unlock();

            //NECESSÁRIO PARA MULTITHREAD
        /*this.missingChunks_Lock.lock();
            for(int i = 0; i < totalNumberOfChunks && !missingc[i]; i++)
                this.mi.missingChunks[i] = false;
//        this.mi.missingChunks[i] = this.mi.missingChunks[i] & missingc[i];
        this.missingChunks_Lock.unlock();*/

        this.mi_Lock.lock();
            if (this.mi.numberOfChunksInArray == this.mi.numberOfChunks)
                    this.mi.full = true;
            if(this.mi.numberOfChunksInArray > this.mi.numberOfChunks)
                System.out.println("                            MAIS CHUNKS DO QUE O QUE DEVIA DE TER (ADDCHUNKS");

        this.mi_Lock.unlock();

        updateDocumentMetaInfoFile();

        new Thread(() ->{

            this.memoryWriter_Lock.lock();
            this.chunkMessagesToWrite.add(chunkMessages);

            if(!this.isThreadWriting) {
                this.isThreadWriting = true;
                while (this.chunkMessagesToWrite.size() > 0) {
                    ChunkMessage[] cm = this.chunkMessagesToWrite.get(0);
                    this.chunkMessagesToWrite.remove(0);

                    this.memoryWriter_Lock.unlock();

                    //long beforeWriting = System.currentTimeMillis();
                    int writtenChunks = 0;
                    Chunk[] chunks = new Chunk[cm.length];
                    for (int j = 0; j < chunks.length; j++)
                        chunks[j] = cm[j].chunk;

                    writtenChunks += writeChunksToFolder(chunks);
                    //long afterWriting = System.currentTimeMillis();

                    //System.out.println("            TIME SPENT WRITTING " + (afterWriting - beforeWriting) + " ms ( " + chunks.size() + " Chunks )");
                    this.memoryWriter_Lock.lock();
                    this.numberOfWrittenChunks += writtenChunks;
                }
                this.isThreadWriting = false;
                if(this.numberOfWrittenChunks == this.mi.numberOfChunks)
                    this.isWrittenToMemory = true;
            }
            this.memoryWriter_Lock.unlock();
        }).start();

        return this.mi.full;
    }

    /*
    * Receiving a ArrayList of Byte Arrays, this function creates all the corresponding FileChunks
    *   ArrayList<byte[]> fileAsBytesChunks => A ArrayList that contains Byte[] that contain information
    *   id => ID of the last Filechunk of the arraylist
    * */
    private Chunk[] createChunks(byte[][] fileAsBytesChunks, int maxindex, int id){
        int noc = maxindex;
        int fcID = id - noc + Integer.MIN_VALUE;

        Chunk[] res = new Chunk[maxindex];

        for (int i = 0; i < noc; i++) {
            res[i] = new Chunk(fileAsBytesChunks[i], fcID++);
        }
        return res;
    }

    /*
    * Given a startID and "len" number of FileChunks, this function will retrieve "len" consecutive FileChunks from the given StartID
    *   int start => Starting ID to retrieve
    *   int len => Number of Filechunks to Retrieve after the start ID
    * */
    public ArrayList<Chunk> getChunks(int start, int len){
        start += Integer.MIN_VALUE;
        String tmpFolder = this.Root + this.mi.Hash + "/Chunks/";

        File document = new File (tmpFolder);
        ArrayList<Chunk> fChunks = null;

        try {
            if(document.exists() && document.isDirectory()){
                fChunks = new ArrayList<Chunk>();

                for(int i = 0; i < len; i++, start++){
                    fChunks.add(new Chunk(Files.readAllBytes(Paths.get(tmpFolder + start + ".chunk")), start));
                }

            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return fChunks;
    }

    /*
     * Given an ArrayList with Filechunk IDs, this will retrieve them.
     *   ArrayList<Integer> mfc => ArrayList that contains IDs of Filechunks
     *
     * This function is intended to be used to retrieve FileChunks that have been marked as Missing by a File Receiver.
     */
    public Chunk[] getMissingChunks(int[] mcID){
        String folder = this.Root + this.mi.Hash + "/Chunks/";

        File folderFile = new File (folder);
        Chunk[] res = new Chunk[mcID.length];
        int id;
        try {
            if(folderFile.exists() && folderFile.isDirectory()){
                Chunk f;
               /* byte[] buffer = new byte[this.mi.datagramMaxSize];
                byte[] Chunk;
                int lastChunkSize =(int) (this.mi.chunksSize % ((this.mi.numberOfChunks-1) * this.mi.datagramMaxSize));
                int lastChunkID = this.mi.numberOfChunks-1 + Integer.MIN_VALUE;
                int j, k;*/
                for(int i = 0; i < mcID.length; i++){
                    id = mcID[i];
                   /* FileInputStream fis = new FileInputStream(new File(tmpFolder + "/" + id + ".chunk"));
                    BufferedInputStream bis = new BufferedInputStream(fis);


                    k = 0;
                    if(id != lastChunkID) {
                        Chunk = new byte[this.mi.datagramMaxSize];
                        while((j = bis.read(buffer, 0, this.mi.datagramMaxSize)) != -1){
                            System.arraycopy(buffer, 0, Chunk, k, j);
                            k += j;
                        };
                    }
                    else{
                        Chunk = new byte[lastChunkSize];
                        while((j = bis.read(buffer, 0, lastChunkSize)) != -1){
                            System.arraycopy(buffer, 0, Chunk, k, j);
                            k += j;
                        };
                    }
                    f = new Chunk(Chunk, id);
                    fis.close();
                    bis.close();*/
                    f = new Chunk(Files.readAllBytes(Paths.get(folder + "/" + id + ".chunk")), id);
                    res[i] = f;
                }

            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return res;
    }

    /*
    * Retrieves the IDs of the Missing Filechunks. These Filechunks are the ones that haven't been sent or haven't been received yet.
    */
    public boolean[] getMissingChunksIDs(){
        boolean[] res = new boolean[this.mi.numberOfChunks];

        System.arraycopy(this.mi.missingChunks, 0, res, 0, this.mi.numberOfChunks);

        return res;
    }


    public ArrayList<CompressedMissingChunksID> getCompressedMissingChunksID(int maxSize){
        int totalNumberOfChunks = this.mi.numberOfChunks;

        ArrayList<CompressedMissingChunksID> res = new ArrayList<CompressedMissingChunksID>();

        int actualMaxSize = maxSize+1;

        int numberOfMissingChunks = 0;

        this.missingChunks_Lock.lock();

            for(boolean val : this.mi.missingChunks)
                if(val){
                    numberOfMissingChunks++;
                }
        this.missingChunks_Lock.unlock();

        if(numberOfMissingChunks == totalNumberOfChunks)
            res = null;
        else{
            int sizeParam = 4 * maxSize;
            boolean [] missingChunks = new boolean[totalNumberOfChunks];

            while(res.size() != 1 && (actualMaxSize > maxSize || actualMaxSize < maxSize*0.5)) {
                res.clear();

                //COPY
                System.arraycopy(this.mi.missingChunks, 0, missingChunks, 0, totalNumberOfChunks);

                int nmc = numberOfMissingChunks;

                while (nmc > 0) {
                    System.out.println("A Processar Compressed MissingChunks " + nmc + " left ( MAX SIZE: " + maxSize + " )");
                    CompressedMissingChunksID cmcID = getCompressedMissingChunksID(missingChunks, (int)(sizeParam*0.9));
                    res.add(cmcID);

                    nmc = 0;
                    for (boolean val : missingChunks)
                        if(val)
                            nmc++;
                }
                actualMaxSize = maxSize(res);
                System.out.println("ACTUAL SIZE " + actualMaxSize + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

                if(actualMaxSize > maxSize) {
                    sizeParam = (int) (sizeParam * 0.95);
                    System.out.println("DEMASIADO GRANDE " + actualMaxSize + " vs " + maxSize);
                }
                else {
                    if (actualMaxSize < maxSize * 0.5) {
                        sizeParam = (int) (sizeParam * 1.05);
                        System.out.println("DEMASIADO PEQUENO " + actualMaxSize + " vs " + maxSize);
                        //DEBUG!!!!!!!!!!!!!!!!!!!!!!!!
                        if(actualMaxSize == Integer.MIN_VALUE){
                            System.out.println("RES SIZE = " + res.size());

                            boolean isThereMissingChunk = false;
                            for(boolean b : this.mi.missingChunks)
                                isThereMissingChunk = isThereMissingChunk || b;

                            System.out.println("IS THERE ANY THIS.MISSING CHUNKS " + isThereMissingChunk);

                            isThereMissingChunk = false;
                            for(boolean b : missingChunks)
                                isThereMissingChunk = isThereMissingChunk || b;
                            System.out.println("IS THERE ANY MISSING CHUNKS " + isThereMissingChunk);

                            System.out.println("NMC " + nmc);
                            System.out.println("RES VALUES");
                            for (CompressedMissingChunksID c : res)
                                System.out.println(Arrays.toString(getIDsFromCompressedMissingChunksID(c)));
                            try {
                                Thread.sleep(10000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }

        }

        System.out.println("FINAL ACTUALMAXSIZE => " + actualMaxSize);

        //Debug
        //check(res);

        return res;
    }

    private void check(ArrayList<CompressedMissingChunksID> cmcids){
        System.out.println("        IM STILL CHECKING THE CMCIDS");
        int numberOfMissingChunks = 0;

        for(int i = 0; i < this.mi.missingChunks.length; i++) {
            if(this.mi.missingChunks[i])
                numberOfMissingChunks++;
        }
        if(cmcids == null){
            if(this.mi.numberOfChunks != numberOfMissingChunks)
                System.out.println("BHBHBHBHBHBHHBHBHBHBHBHBHHBHBHBHBHBHBHBHBHBHHBHBHBHBHBHBHHBHBHBHBHBHBHBHBHBHHBHBHBHBHBHBHHBHBHBHBHBHBHBHBHBHHBHBHBHBHBHBHHBHBHBHBHBHBHBHBHBHHBHBHBHBHBHBHHBHBHBHBHBHBHBHBHBHHBHBHBHBHBHBHHBHBHBHBHBHBHBHBHBHHBHBHBHBHBHBHHBHBHBHBHBHBHBHBHBHHBHBHBHBHBHBHHBHBHBHBHBHBHBHBHBHHBHBHBHBHBHBHHBHBHBHBHBHBHBHBHBHHBHBHBHBHBHBHHBHBHBHBHBHBHBHBHBHHBHBHBHBHBHBHHBHBHBHBHBHBHBHBHBHHBHBHBHBHBHBHHBHBHBHBHBHBHBHBHBHHBHBHBHBHBHBHHBHBHBHBHBHBHBHBHBHHBHBHBHBHBHBHHBHBHBHBHBHBHBHBHBHHBHBHBHBHBHBHHBHBHBHBHBHBHBHBHBHHBHBHBHBHBHBHHBHBHBHBHBHBHBHBHBHHBHBHBHBHBHBHHBHBHBH");
        }
        else {

            //int[] convertedCMCIDs = new int[numberOfMissingChunks];
            ArrayList<Integer> convertedCMCIDs = new ArrayList<>();
            int[] aux;
            int idsWritten = 0;
            for (CompressedMissingChunksID cmcid : cmcids) {
                aux = getIDsFromCompressedMissingChunksID(cmcid);
                for(int val : aux)
                    convertedCMCIDs.add(val);

                //System.arraycopy(aux, 0, convertedCMCIDs, idsWritten, aux.length);
            }


            if(convertedCMCIDs.size() != numberOfMissingChunks)
                System.out.println("WHATWHATWHATWHATWHATWHATWHATWHATWHATWHATWHATWHATWHATWHATWHATWHATWHATWHATWHATWHATWHATWHATWHATWHATWHATWHAT\n" + convertedCMCIDs.size() + " " + numberOfMissingChunks);
            for (int i = 0; i < numberOfMissingChunks; i++) {
                if (this.mi.missingChunks[i]) {
                    if (!convertedCMCIDs.contains(i + Integer.MIN_VALUE)) {
                        System.out.println("                                        DOES NOT HAVE i + Integer.MIN_VALUE " + (i + Integer.MIN_VALUE));
                    }
                }
            }
        }
    }

    private int maxSize (ArrayList <CompressedMissingChunksID> cmcIDs){
        byte[] sizeTester;
        int maxSize = Integer.MIN_VALUE;

        for(CompressedMissingChunksID cmcid : cmcIDs){
            sizeTester = getBytesFromObject(cmcid);
            if(sizeTester.length > maxSize)
                maxSize = sizeTester.length;
        }

        return maxSize;
    }

    private byte[] getBytesFromObject(Object obj) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = null;

        try {
            out = new ObjectOutputStream(bos);
            out.writeObject(obj);
            out.flush();

        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            try {
                bos.close();
            }
            catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        byte[] data = bos.toByteArray();

        return data;
    }

    private CompressedMissingChunksID getCompressedMissingChunksID(boolean[] missingChunks, int maxSize){

        int totalNumberOfChunks = this.mi.numberOfChunks;

        int[] referenceIDArray = new int[2];
        boolean[][] toAddArray = new boolean[2][];
        byte[][] incArray = new byte[2][];
        int groupPointer = 0;

        int referenceID;
        boolean[] toAdd = new boolean[300];
        int toAddPointer = 0;
        byte[] inc = new byte[300];
        int incPointer = 0;

        int currentID = 0;
        int mcPointer = 0;

        for(int i = 0; i < totalNumberOfChunks && mcPointer == 0; i++)
            if(missingChunks[i]){
                missingChunks[i] = false;
                mcPointer = i+1;
                referenceID = i + Integer.MIN_VALUE;
                currentID = referenceID;
                referenceIDArray[groupPointer] = referenceID;
            }

        int currentSize = Integer.SIZE;

        int nextID;
        int maxByteValue = Byte.MAX_VALUE - Byte.MIN_VALUE;
        int diff;
        int consecutiveFalses;
        int increment;


        while (currentSize < maxSize &&  mcPointer < totalNumberOfChunks) {
            //System.out.println("    CURRENT SIZE " + currentSize + " MAX SIZE " + maxSize + "\n i " + i + " mfc.size " + mfcGroup.size());
            nextID = 0;
            // procurar o proximo ID a representar na estrutura
            while (mcPointer < totalNumberOfChunks && nextID == 0) {
                if (missingChunks[mcPointer]) {
                    missingChunks[mcPointer] = false;
                    nextID = mcPointer + Integer.MIN_VALUE;
                }
                mcPointer++;
            }

            if (nextID != 0) {

                diff = nextID - currentID;
                if(diff < 6120){
                    consecutiveFalses = diff / maxByteValue;
                    increment = diff % maxByteValue;

                    //verificar espaço do array toAdd
                    if(toAddPointer + (consecutiveFalses + 1) > toAdd.length){
                        boolean[] newToAdd = new boolean[toAdd.length*2];
                        System.arraycopy(toAdd, 0, newToAdd, 0, toAddPointer);
                        toAdd = newToAdd;
                    }

                    //preencher o Array toAdd
                    for(int i = 0; i < consecutiveFalses; i++) {
                        toAdd[toAddPointer] = false;
                        toAddPointer++;
                    }
                    toAdd[toAddPointer] = true;
                    toAddPointer++;

                    //verificar espaço no array inc
                    if(incPointer + 1 > inc.length){
                        byte[] newInc = new byte[inc.length*2];
                        System.arraycopy(inc, 0, newInc, 0, incPointer);
                        inc = newInc;
                    }

                    //adicionar elemento ao array inc

                    inc[incPointer] = (byte) (increment + Byte.MIN_VALUE);
                    incPointer++;

                    currentSize += (consecutiveFalses + 1 + Byte.SIZE) ;
                }
                else{
                    System.out.println("            Saved Space in the compressed Chunk IDs");

                    //Crop array toAdd to size
                    boolean[] croppedToAdd = new boolean[toAddPointer];
                    System.arraycopy(toAdd, 0, croppedToAdd, 0, toAddPointer);

                    //Adicionar o array à lista de grupos
                    toAddArray[groupPointer] = croppedToAdd;
                    toAddPointer = 0;

                    //Crop array inc to size
                    byte[] croppedInc = new byte[incPointer];
                    System.arraycopy(inc, 0, croppedInc, 0, incPointer);

                    //Adicionar o array à lista de grupos
                    incArray[groupPointer] = croppedInc;
                    incPointer = 0;

                    toAdd = new boolean[300];
                    inc = new byte[300];

                    groupPointer++;

                    /////////////////////////////////////////////////////////////////////////////////////////////
                    //New group//////////////////////////////////////////////////////////////////////////////////
                    /////////////////////////////////////////////////////////////////////////////////////////////

                    //DEBUG!!!!!
                    if(groupPointer + 1 > referenceIDArray.length){

                       /* System.out.println("BEFORE RID EXTENSION");
                        System.out.println(Arrays.toString(referenceIDArray));*/

                        //verificar espaço no array ReferenceIDArray
                        int[] newReferenceIDArray = new int[referenceIDArray.length+2];
                        System.arraycopy(referenceIDArray, 0, newReferenceIDArray, 0, groupPointer);
                        referenceIDArray = newReferenceIDArray;

                        /*System.out.println("AFTER RID EXTENSION");
                        System.out.println(Arrays.toString(referenceIDArray));

                        System.out.println("BEFORE INCARRAY EXTENSION");
                        System.out.println(Arrays.toString(incArray));*/

                        //verificar espaço no array incArray
                        byte[][] newIncArray = new byte[incArray.length+2][];
                        System.arraycopy(incArray, 0, newIncArray, 0, groupPointer);
                        incArray = newIncArray;

                        /*System.out.println("AFTER INCARRAY EXTENSION");
                        System.out.println(Arrays.toString(incArray));


                        System.out.println("BEFORE TOADD EXTENSION ");
                        System.out.println(Arrays.toString(toAdd));
*/
                        //verificar espaço no toAddArray
                        boolean[][] newToAddArray = new boolean[toAddArray.length+2][];
                        System.arraycopy(toAddArray, 0, newToAddArray, 0, groupPointer);
                        toAddArray = newToAddArray;

                       /* System.out.println("AFTER TOADD EXTENSION ");
                        System.out.println(Arrays.toString(toAdd));*/
                    }

                    //Adicionar elemento ao array referenceIDArray
                    referenceIDArray[groupPointer] = nextID;

                    currentSize += Integer.SIZE;
                }

                currentID = nextID;
            }
        }

        //Crop array toAdd to size
        boolean[] croppedToAdd = new boolean[toAddPointer];
        System.arraycopy(toAdd, 0, croppedToAdd, 0, toAddPointer);

        //Adicionar o array à lista de grupos
        toAddArray[groupPointer] = croppedToAdd;

        //Crop array inc to size
        byte[] croppedInc = new byte[incPointer];
        System.arraycopy(inc, 0, croppedInc, 0, incPointer);

        //Adicionar o aray à lista de grupos
        incArray[groupPointer] = croppedInc;

        groupPointer++;

        /*System.out.println("BEFORE RID CROP ");
        System.out.println(Arrays.toString(referenceIDArray));*/
        //Crop referenceIDArray
        int[] croppedRID = new int[groupPointer];
        System.arraycopy(referenceIDArray, 0, croppedRID, 0, groupPointer);

       /* System.out.println("AFTER RID CROP ");
        System.out.println(Arrays.toString(croppedRID));*/

        /*System.out.println("BEFORE TAA CROP ");
        System.out.println(Arrays.toString(toAddArray));*/
        //Crop toAddArray
        boolean[][] croppedTAA = new boolean[groupPointer][];
        System.arraycopy(toAddArray, 0, croppedTAA, 0, groupPointer);
/*        System.out.println("AFTER TAA CROP ");
        System.out.println(Arrays.toString(croppedTAA));*/

        /*System.out.println("BEFORE IA CROP ");
        System.out.println(Arrays.toString(incArray));*/
        //Crop incArray
        byte[][] croppedIA = new byte[groupPointer][];
        System.arraycopy(incArray, 0, croppedIA, 0, groupPointer);
        /*System.out.println("AFTER IA CROP ");
        System.out.println(Arrays.toString(croppedIA));
*/
        return new CompressedMissingChunksID(croppedRID, croppedTAA, croppedIA);
    }

    public int[] getIDsFromCompressedMissingChunksID(CompressedMissingChunksID cmcid){

/*        int a = 0;
        for(int rid : cmcid.referenceID){
            System.out.println("ReferenceID => " + rid);
            for(boolean b : cmcid.toAdd[a])
                System.out.print(b + " ");
            System.out.println("");

            for(byte v : cmcid.increments[a])
                System.out.print(v + " ");
            System.out.println("");

            a++;
        }*/

        int numberOfIDs = 0;

        for(int i = 0; i < cmcid.referenceID.length; i++) {
            numberOfIDs++;
            if(cmcid.increments[i] != null)
                numberOfIDs += cmcid.increments[i].length;
        }

        int[] res = new int[numberOfIDs];
        int pointer = 0;
        int maxValue = Byte.MAX_VALUE - Byte.MIN_VALUE;
        int currentID;


        int toAddAux;
        int i;

        for(int k = 0; k < cmcid.referenceID.length; k++) {
            currentID = cmcid.referenceID[k];
            res[pointer] = currentID;
            pointer++;

            i = 0;
            for (int j = 0; cmcid.increments[k] != null && j < cmcid.increments[k].length; j++) {
                toAddAux = (int) cmcid.increments[k][j] - Byte.MIN_VALUE;

                while (i < cmcid.toAdd[k].length && !cmcid.toAdd[k][i]) {
                    currentID += maxValue;
                    i++;
                }

                if (i < cmcid.toAdd[k].length) {
                    currentID += toAddAux;
                    res[pointer] = currentID;
                    pointer++;
                    i++;
                }
            }
        }

        return res;
    }

    /*
    * Retrieves the amount of Missing Filechunks
    */
    public int getNumberOfMissingChunks(){
        int numberOfMissingChunks = 0;
        if(this.mi.missingChunks != null)
            for(boolean val : this.mi.missingChunks)
                if(val)
                    numberOfMissingChunks++;

        return numberOfMissingChunks;
    }

    /*
    * Calculates the hash of the provided ArrayList<Chunk>
    * */
    private String hash_Chunks(Chunk[] chunks, String alg){
        Hash h = new Hash(alg);

        for(Chunk c : chunks) {
            h.updateHash(c.Chunk);
        }

        return h.extractHash();
    }

    /*
     * Calculates the hash of the provided ArrayList<byte[]>
     * */
    private String hash_Bytes(ArrayList<byte[]> bytes, String alg){
        Hash h = new Hash(alg);

        for(byte[] b : bytes) {
            h.updateHash(b);
        }

        return h.extractHash();
    }

    /*
     * Checks if the provided ArrayList<Chunk> hash matches the provided hash
     * */
    public boolean checkHash_Chunks(Chunk[] chunks, String hash, String alg){
        return hash.equals(hash_Chunks(chunks, alg));
    }

    /*
     * Checks if the provided ArrayList<byte[]> hash matches the provided hash
     * */
    public boolean checkHash_Bytes(ArrayList<byte[]> bytes, String hash, String alg){
        return hash.equals(hash_Bytes(bytes, alg));
    }

    public boolean checkAllChunksHash(){
        Long start = System.currentTimeMillis();
        Hash h = new Hash(this.mi.HashAlgorithm);

        String folderPath = this.Root + this.mi.Hash + "/Chunks/";
        Path p;

        for(int i = 0; i < this.mi.numberOfChunks; i++){
            try {
                p = Paths.get(folderPath + (i + Integer.MIN_VALUE) + ".chunk");
                h.updateHash(Files.readAllBytes(p));
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        System.out.println("CHECK HASH TIME => " + (System.currentTimeMillis() - start) + " ms");
        return this.mi.Hash.equals(h.extractHash());
    }
    /*
    * Deletes all Chunks that are saved in Memory as well the Data.DocumentMetaInfo File
    * */
    public void eraseChunks(){


        String path = this.Root + this.mi.Hash + "/";
        File c = new File(path + "/Chunks");
        try {
            FileUtils.cleanDirectory(c);
        } catch (IOException e) {
            e.printStackTrace();
        }


        String documentMetainfoFile = path + "ChunkManagerMeta.info";
        c = new File(documentMetainfoFile);
        while(c.exists() && !c.delete());

        String chunksFolder = path + "Chunks/";
        c = new File(chunksFolder);
        while(c.exists() && !c.delete());

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

    public ChunkManagerMetaInfo getCMMI(){
        return this.mi;
    }
}
