package Data;

import java.io.File;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.io.FileUtils;


public class ChunkManager {

    /*
    * These 3 parameters are used to construct a path where the Filechunks of the Information can be found
    */
    private String Root;

    private ChunkManagerMetaInfo mi;
    private ReentrantLock mi_Lock;
    private ReentrantLock missingChunks_Lock;

    /*
    * Creates a basic ChunkManager object that is used to initialize the class from a DocumentMetaInfo file
    * */
    public ChunkManager(String Root){
        Root = folderPathNormalizer(Root);
        this.Root = Root;
        this.mi_Lock = new ReentrantLock();
    }

    /*
    * Creates a ChunkManager Object for a File to be received and written to Memory
    *   int numberOfChunks => Number of Chunks this File is composed of
    *   String mac => MAC address of the Node who's sending the File
    *   String name => Name of the File
    *   String hash => Hash of the File
    */

    public ChunkManager (String root, String hash, String hashAlgorithm,  int numberOfChunks){
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

        this.mi.numberOfChunks = numberOfChunks;
        this.mi.numberOfChunksInArray = 0;
        this.mi.chunksSize = 0;
        this.mi.full = false;
        this.mi.Hash = hash;
        this.mi.HashAlgoritm = hashAlgorithm;

        this.missingChunks_Lock = new ReentrantLock();
        this.mi.missingChunks = new boolean[numberOfChunks];
        this.mi.missingChunks[0] = true;

        for (int i = 1; i < numberOfChunks; i += i) {
            System.arraycopy(this.mi.missingChunks, 0, this.mi.missingChunks, i, Math.min((numberOfChunks - i), i));
        }

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
        ;
        this.mi.numberOfChunksInArray = 0;
        this.mi.chunksSize = info.length;

        ArrayList <Chunk> chunks = createChunks(splitInfoIntoArrays(info), this.mi.numberOfChunks);

        this.mi.HashAlgoritm = hashAlgorithm;
        this.mi.Hash = hash_Chunks(chunks, hashAlgorithm);

        File hashFolder = new File(this.Root + "/" + this.mi.Hash + "/");
        while (!hashFolder.exists() && !hashFolder.isDirectory() && !hashFolder.mkdir()) ;

        writeChunksToFolder(chunks);
        this.mi.full = true;

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
        this.mi.HashAlgoritm = hashAlgorithm;

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
        Hash h = new Hash(this.mi.HashAlgoritm);
        try {
            FileInputStream fis = new FileInputStream(localFilePath);

            int i = 0;
            int read = 1;
            long readlimit;
            ArrayList<byte[]> info = new ArrayList<byte[]>();
            byte[] buffer;
            ArrayList<Chunk> Chunks;

            while(i < this.mi.numberOfChunks){

                for(int j = 0; j < maxChunksLoadedAtaTime && i < this.mi.numberOfChunks && read != 0; i++, j++){
                    if(i == this.mi.numberOfChunks-1)
                        readlimit = this.mi.chunksSize - this.mi.datagramMaxSize * i;
                    else
                        readlimit = this.mi.datagramMaxSize;
                    buffer = new byte[(int) readlimit];
                    read = fis.read(buffer);
                    h.updateHash(buffer);
                    info.add(buffer);
                }
                Chunks = createChunks(info, i);
                writeChunksToFolder(Chunks);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return h.extractHash();
    }

    /*
    * This will split a byte[] into multiple byte[] based on the mi.datagramMaxSize
    * Returns a ArrayList with the corresponding byte[]
    * */
    private ArrayList <byte[]> splitInfoIntoArrays(byte[] info){
        ArrayList<byte[]> splits = new ArrayList<byte[]>();

        int i = 0;
        long readlimit;

        while(i < this.mi.numberOfChunks) {
            if(i == this.mi.numberOfChunks-1)
                readlimit = (this.mi.datagramMaxSize * i) + this.mi.chunksSize - this.mi.datagramMaxSize*i;
            else
                readlimit = this.mi.datagramMaxSize * (i + 1);

            //System.out.println("READ FROM " + this.mi.datagramMaxSize * i + " TO " + readlimit + "( " + this.mi.chunksSize +  " )");
            splits.add(Arrays.copyOfRange(info, this.mi.datagramMaxSize * i, (int) readlimit));
            i++;
        }

        return splits;
    }

    /*
    * Writes a given number of FileChunks to a specified Folder within the MAC Folder
    *   FileChunk[] fcs => Array of FileChunks to be written
    *   int size => Size of the FileChunk Array
    */
    private void writeChunksToFolder(ArrayList <Chunk> fcs){
        int i;
        int size = fcs.size();

        String folderPath = this.Root + this.mi.Hash + "/Chunks/";
        File ficheiro = new File(folderPath);
        File filePointer;
        String path;

        while((!ficheiro.exists() && !ficheiro.isDirectory()) && !ficheiro.mkdir());

        Path file;
        for(i = 0; i < size; i++){
            try {
                Chunk fc = fcs.get(i);
                path = folderPath + fc.place + ".chunk";
                filePointer = new File(path);

                //SO VAI ESCREVER O FICHEIRO SE ELE NAO EXISTIR
                if(!filePointer.exists()) {
                    file = Paths.get(path);
                    Files.write(file, fc.Chunk);
                }
                else
                    System.out.println("REPEATED CHUNK" + fc.place);
            }
                catch (Exception e) {
                e.printStackTrace();
            }
        }
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
    public boolean addChunks (ArrayList<Chunk> chunks){

        int chunk_size = chunks.size();
        int totalNumberOfChunks = this.mi.numberOfChunks;

        //DEBUG!!!!!!!!!!!!!!!!!!
        if(chunk_size == 0){
            System.out.println("            SAY WHATTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT");
            try {
                Thread.sleep(10000000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

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

        boolean[] missingc = new boolean[totalNumberOfChunks];
        missingc[0] = true;

        for (int i = 1; i < totalNumberOfChunks; i += i)
            System.arraycopy(missingc, 0, missingc, i, Math.min((totalNumberOfChunks - i), i));

        this.mi_Lock.lock();
            for (Chunk c : chunks) {
                missingc[c.place - Integer.MIN_VALUE] = false;
                this.mi.numberOfChunksInArray++;
                this.mi.chunksSize += c.Chunk.length;
            }
        this.mi_Lock.unlock();

        this.missingChunks_Lock.lock();
            for(int i = 0; i < totalNumberOfChunks; i++)
                this.mi.missingChunks[i] = this.mi.missingChunks[i] & missingc[i];
        this.missingChunks_Lock.unlock();

        this.mi_Lock.lock();
            if (this.mi.numberOfChunksInArray == totalNumberOfChunks)
                    this.mi.full = true;

        this.mi_Lock.unlock();

        updateDocumentMetaInfoFile();

        new Thread(() ->{
            //System.out.println("            Inside Writing THREAD");

            //long beforeWriting = System.currentTimeMillis();
            writeChunksToFolder(chunks);
            //long afterWriting = System.currentTimeMillis();

            //System.out.println("            TIME SPENT WRITTING " + (afterWriting - beforeWriting) + " ms ( " + chunks.size() + " Chunks )");
        }).start();

        return this.mi.full;
    }

    /*
    * Receiving a ArrayList of Byte Arrays, this function creates all the corresponding FileChunks
    *   ArrayList<byte[]> fileAsBytesChunks => A ArrayList that contains Byte[] that contain information
    *   id => ID of the first Filechunk of the arraylist
    * */
    private ArrayList<Chunk> createChunks(ArrayList<byte[]> fileAsBytesChunks, int id){
        int noc = fileAsBytesChunks.size();
        int fcID = id - noc + Integer.MIN_VALUE;

        ArrayList <Chunk> res = new ArrayList<Chunk>();

        for (int i = 0; i < noc; i++) {
            res.add(new Chunk(fileAsBytesChunks.get(i), fcID++));
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
    public ArrayList<Chunk> getMissingChunks(ArrayList<Integer> mfc){
        String tmpFolder = this.Root + this.mi.Hash + "/Chunks/";

        File ficheiro = new File (tmpFolder);
        ArrayList<Chunk> res = new ArrayList<Chunk>();
        int id;
        try {
            if(ficheiro.exists() && ficheiro.isDirectory()){
                Chunk f;
                for(int i = 0; i < mfc.size(); i++){
                    id = mfc.get(i);
                    f = new Chunk(Files.readAllBytes(Paths.get(tmpFolder + "/" + id + ".chunk")), id);
                    res.add(f);
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
        boolean [] missingChunks = new boolean[totalNumberOfChunks];

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

            while(res.size() != 1 && (actualMaxSize > maxSize || actualMaxSize < maxSize*0.9)) {
                res.clear();

                //COPY
                System.arraycopy(this.mi.missingChunks, 0, missingChunks, 0, totalNumberOfChunks);

                int nmc = numberOfMissingChunks;

                while (nmc > 0) {
                    System.out.println("A Processar Compressed MissingChunks " + nmc + " left ( MAX SIZE: " + maxSize + " )");
                    CompressedMissingChunksID cmcID = getCompressedMissingChunksID(missingChunks, sizeParam);
                    res.add(cmcID);

                    nmc = 0;
                    for (boolean val : missingChunks)
                        if(val)
                            nmc++;
                }
                actualMaxSize = maxSize(res);

                if(actualMaxSize > maxSize) {
                    sizeParam = (int) (sizeParam * 0.95);
                    System.out.println("DEMASIADO GRANDE " + actualMaxSize + " vs " + maxSize);
                }
                else {
                    if (actualMaxSize < maxSize * 0.9) {
                        sizeParam = (int) (sizeParam * 1.05);
                        System.out.println("DEMASIADO PEQUENO " + actualMaxSize + " vs " + maxSize);
                        //DEBUG!!!!!!!!!!!!!!!!!!!!!!!!
                        if(actualMaxSize == Integer.MIN_VALUE){
                            System.out.println("RES SIZE = " + res.size());
                            System.out.println("THIS.MISSING CHUNKS " + this.mi.missingChunks);
                            System.out.println("MISSING CHUNKS " + missingChunks);
                            System.out.println("NMC " + nmc);
                            System.out.println("RES VALUES");
                            for (CompressedMissingChunksID c : res)
                                System.out.println(getIDsFromCompressedMissingChunksID(c));
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
        return res;
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

        //System.out.println(mfcGroup);
        int totalNumberOfChunks = this.mi.numberOfChunks;
        //ArrayList<Integer> mfcGroup = missingChunks;
        int mcPointer = 0;
        int referenceID = 0;
        int currentID = 0;

        for(int i = 0; i < totalNumberOfChunks && mcPointer == 0; i++)
            if(missingChunks[i]){
                missingChunks[i] = false;
                mcPointer = i+1;
                referenceID = i + Integer.MIN_VALUE;
                currentID = referenceID;
            }

        int currentSize = Integer.SIZE;

        ArrayList<Boolean> toAdd = new ArrayList<Boolean>();

        ArrayList<Byte> inc = new ArrayList<Byte>();

        int dif = Byte.MAX_VALUE - Byte.MIN_VALUE;
        int nextID;
        // System.out.println("CURRENT SIZE " + currentSize + " MAX SIZE " + maxSize);
        while (currentSize < maxSize &&  mcPointer < totalNumberOfChunks) {
            //System.out.println("    CURRENT SIZE " + currentSize + " MAX SIZE " + maxSize + "\n i " + i + " mfc.size " + mfcGroup.size());
            nextID = 0;
            while (mcPointer < totalNumberOfChunks && nextID == 0) {
                if (missingChunks[mcPointer]) {
                    missingChunks[mcPointer] = false;
                    nextID = mcPointer + Integer.MIN_VALUE;
                }
                mcPointer++;
            }

            if (nextID != 0) {
                //System.out.println(currentID + " + " + dif + " < " + nextID + " " + mfcGroup.isEmpty());
                while (currentID + dif < nextID) {
                    //System.out.println("        CURRENT SIZE " + currentSize + " MAX SIZE " + maxSize);
                    toAdd.add(false);
                    currentID += dif;
                    currentSize += 1;
                }

                toAdd.add(true);
                currentSize += 1;

                //System.out.println("INC TO ADD => " + (byte)(nextID - currentID + Byte.MIN_VALUE));
                inc.add((byte) (nextID - currentID + Byte.MIN_VALUE));
                currentSize += Byte.SIZE;
                currentID = nextID;

            }
        }

        //System.out.println("MAX SIZE => " + maxSize + " | CURRENTSIZE => " + currentSize);

        //System.out.println("\tINC SIZE => " + inc.size() + "\n\tTO ADD SIZE => " + toAdd.size() + "\n\tINVERTED TO ADO SIZ => " + invertedToAdd.size() + "\n\tPOINTER => " + pointer);
        boolean[] toAddArray = new boolean[toAdd.size()];
        for (int i = 0; i < toAdd.size(); i++)
            toAddArray[i] = toAdd.get(i);

        byte[] incArray = new byte[inc.size()];
        for (int i = 0; i < inc.size(); i++) {
            //System.out.println("inc[" + i + "] = " + inc.get(i));
            incArray[i] = inc.get(i);
        }

        //System.out.println("TENHO " + counter +1 + " IDS");

        return new CompressedMissingChunksID(referenceID, toAddArray, incArray);
    }

    public ArrayList<Integer> getIDsFromCompressedMissingChunksID(CompressedMissingChunksID cmcid){
        ArrayList<Integer> res = new ArrayList<Integer>();

        int currentID = cmcid.referenceID;

        int maxValue = Byte.MAX_VALUE - Byte.MIN_VALUE;

        res.add(currentID);
        int i = 0;
        int toAddAux;

        for(byte increment : cmcid.increments){
            toAddAux = (int)increment - Byte.MIN_VALUE;

            while(i < cmcid.toAdd.length && !cmcid.toAdd[i]) {
                currentID += maxValue;
                i++;
            }

            if(i < cmcid.toAdd.length){
                currentID += toAddAux;
                res.add(currentID);
                i++;
            }
        }

        //System.out.println(res);
        return res;
    }

    /*
    * Retrieves The Flag full that indicates if all the FileChunks are present
    */
    public boolean getFull(){
        return this.mi.full;
    }

    /*
    * Retrieves the amount of Missing Filechunks
    */
    public int getNumberOfMissingChunks(){
        int numberOfMissingChunks = 0;
        for(boolean val : this.mi.missingChunks)
            if(val)
                numberOfMissingChunks++;

        return numberOfMissingChunks;
    }

    /*
     * Retrieves the total number of total Chunks this Information is divided into
     * */
    public int getNumberOfChunks(){
        return this.mi.numberOfChunks;
    }

    /*
    * Retrieves the amount of Memory that the Filechunks of this File are taking. (Only the ones that are present on the current Node)
    */
    public long getChunksSize(){
        return this.mi.chunksSize;
    }

    /*
    * Retrieves the sha_256 Hash form the Information of this Data.ChunkManager
    * */
    public String getHash(){
        return this.mi.Hash;
    }

    /*
    * Calculates the hash of the provided ArrayList<Chunk>
    * */
    private String hash_Chunks(ArrayList<Chunk> chunks, String alg){
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
    public boolean checkHash_Chunks(ArrayList<Chunk> chunks, String hash, String alg){
        return hash.equals(hash_Chunks(chunks, alg));
    }

    /*
     * Checks if the provided ArrayList<byte[]> hash matches the provided hash
     * */
    public boolean checkHash_Bytes(ArrayList<byte[]> bytes, String hash, String alg){
        return hash.equals(hash_Bytes(bytes, alg));
    }

    public boolean checkAllChunksHash(){
        Hash h = new Hash(this.mi.HashAlgoritm);

        ArrayList<Chunk> chunks = new ArrayList<Chunk>();
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
