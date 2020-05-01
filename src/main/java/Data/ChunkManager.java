package Data;

import java.io.File;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.commons.io.FileUtils;


public class ChunkManager {

    /*
    * These 3 parameters are used to construct a path where the Filechunks of the Information can be found
    */
    private String Root;
    private String MacAddress;

    private ChunkManagerMetaInfo mi;

    /*
    * Creates a basic ChunkManager object that is used to initialize the class from a DocumentMetaInfo file
    * */
    public ChunkManager(String Root, String MacAddress){
        Root = folderPathNormalizer(Root);
        this.Root = Root;
        this.MacAddress = MacAddress;
    }

    /*
    * Creates a ChunkManager Object for a File to be received and written to Memory
    *   int numberOfChunks => Number of Chunks this File is composed of
    *   String mac => MAC address of the Node who's sending the File
    *   String name => Name of the File
    *   String hash => Hash of the File
    */

    public ChunkManager (String root, String mac, String hash, String hashAlgorithm,  int numberOfChunks){
        root = folderPathNormalizer(root);
        this.Root = root;
        this.MacAddress = mac;

        this.mi = new ChunkManagerMetaInfo();

        this.mi.numberOfChunks = numberOfChunks;
        this.mi.numberOfChunksInArray = 0;
        this.mi.chunksSize = 0;
        this.mi.full = false;
        this.mi.Hash = hash;
        this.mi.HashAlgoritm = hashAlgorithm;

        this.mi.missingChunks = new ArrayList<Integer>();
        int maxID = this.mi.numberOfChunks + Integer.MIN_VALUE;
        for(int i = Integer.MIN_VALUE; i < maxID; i++)
            this.mi.missingChunks.add(i);


        writeDocumentMetaInfoToFile();
    }

    /*
     * Creates a Data.ChunkManager Object for Info to be saved to Memory
     *   String root => Root path to the file scope of the program
     *   String mac => MAC address of the Node who's sending the File
     *   byte[] info => bytes of the information to be saved
     *   int datagramMaxSize => Maximum size a single datagram can take
     */

    public ChunkManager (String root, String mac, byte[] info, int datagramMaxSize, String hashAlgorithm) {
        root = folderPathNormalizer(root);

        this.Root = root;
        this.MacAddress = mac;

        this.mi = new ChunkManagerMetaInfo();


        this.mi.datagramMaxSize = datagramMaxSize;
        this.mi.numberOfChunks = (int) Math.ceil((double) info.length / (double) this.mi.datagramMaxSize);
        ;
        this.mi.numberOfChunksInArray = 0;
        this.mi.chunksSize = info.length;

        ArrayList <Chunk> chunks = createChunks(splitInfoIntoArrays(info), this.mi.numberOfChunks);

        this.mi.HashAlgoritm = hashAlgorithm;
        this.mi.Hash = sha_256_Chunks(chunks, hashAlgorithm);

        File hashFolder = new File(this.Root + "/" + MacAddress + "/" + this.mi.Hash + "/");
        while (!hashFolder.exists() && !hashFolder.isDirectory() && !hashFolder.mkdir()) ;

        writeChunksToFolder(chunks, this.mi.numberOfChunks);
        this.mi.full = true;

        writeDocumentMetaInfoToFile();
    }

    /*
    * Constructor that creates a ChunkManager object. This Constructor will load a local file to
    * chunks and only have maxChunksLoadedAtaTime chunks in Ram at a time
    * */
    public ChunkManager (String root, String mac, String localFilePath, String hashAlgorithm, int datagramMaxSize, int maxChunksLoadedAtaTime) {
        root = folderPathNormalizer(root);

        this.Root = root;
        this.MacAddress = mac;

        this.mi = new ChunkManagerMetaInfo();
        this.mi.Hash = "TMPFILE";
        this.mi.HashAlgoritm = hashAlgorithm;

        File tempFolder = new File(this.Root + "/" + MacAddress + "/" + this.mi.Hash + "/");
        while (!tempFolder.exists() && !tempFolder.isDirectory() && !tempFolder.mkdir()) ;

        this.mi.datagramMaxSize = datagramMaxSize;

        File f = new File(localFilePath);
        this.mi.chunksSize = f.length();
        this.mi.numberOfChunks = (int) Math.ceil((double) this.mi.chunksSize / (double) this.mi.datagramMaxSize);

        this.mi.numberOfChunksInArray = 0;

        String oldpath = this.Root + "/" + MacAddress + "/" + this.mi.Hash + "/Chunks/";
        this.mi.Hash = loadInfoRamEfficient(localFilePath, maxChunksLoadedAtaTime);
        String newpath = this.Root + "/" + MacAddress + "/" + this.mi.Hash + "/Chunks/";

        File hashFolder = new File(this.Root + "/" + MacAddress + "/" + this.mi.Hash + "/");
        while (!hashFolder.exists() && !hashFolder.isDirectory() && !hashFolder.mkdir()) ;
        hashFolder = new File(this.Root + "/" + MacAddress + "/" + this.mi.Hash + "/Chunks/");
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
        String FileInfoPath = this.Root + this.MacAddress + "/" + this.mi.Hash + "/";
        String documentMetaInfoFilePath = FileInfoPath + "ChunkManagerMeta.info";

        File FileInfo = new File(FileInfoPath);

        while((!FileInfo.exists() && !FileInfo.isDirectory()) && !FileInfo.mkdir());

        FileOutputStream fileOut = null;
        try {
            fileOut = new FileOutputStream(documentMetaInfoFilePath);
            ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
            objectOut.writeObject(this.mi);
            objectOut.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
    * Reads the meta info from root/mac/hash and initializes the MetaInfo object
    */
    public void readDocumentMetaInfoFromFile(String hash){
        String FileInfoPath = this.Root + this.MacAddress + "/" + hash + "/";
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
        String FileInfoPath = this.Root + this.MacAddress + "/" + this.mi.Hash + "/";
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
                writeChunksToFolder(Chunks, info.size());
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
    private void writeChunksToFolder(ArrayList <Chunk> fcs, int size){
        int i;
        String folderPath = this.Root + this.MacAddress + "/" + this.mi.Hash + "/Chunks/";
        File ficheiro = new File(folderPath);
        File filePointer;
        String path;

        while((!ficheiro.exists() && !ficheiro.isDirectory()) && !ficheiro.mkdir());

        Path file;
        for(i = 0; i < size; i++){
            try {
                Chunk fc = fcs.get(i);
                path = folderPath + fc.getPlace() + ".chunk";
                filePointer = new File(path);

                //SO VAI ESCREVER O FICHEIRO SE ELE NAO EXISTIR
                if(!filePointer.exists()) {
                    file = Paths.get(path);
                    Files.write(file, fc.getChunk());
                }
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
        String folderToReadPath = this.Root + this.MacAddress + "/" + this.mi.Hash + "/Chunks/";

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

    /*
    * Updates what Filechunks have been received, which ones are missing, what's the current size of the saved Filechunks,
    * calls writeFileChunksToFolder to write the new Filechunks, and updates the MetaInfo File
    *   ArrayList<FileChunk> fcs => Newly received Filechunks to be written
    */
    public boolean addChunks (ArrayList<Chunk> fcs){

        for(Chunk fc: fcs) {
            //System.out.println("ID!!! => " + fc.getPlace());
            if(this.mi.missingChunks.contains(fc.getPlace())) {
                this.mi.missingChunks.remove(new Integer(fc.getPlace()));
                this.mi.numberOfChunksInArray++;
                this.mi.chunksSize += fc.getChunk().length;
            }
        }

        writeChunksToFolder(fcs, fcs.size());

        if(this.mi.numberOfChunksInArray == this.mi.numberOfChunks) {
            this.mi.full = true;
        }

        updateDocumentMetaInfoFile();

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
        String tmpFolder = this.Root + this.MacAddress + "/" + this.mi.Hash + "/Chunks/";

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

        String tmpFolder = this.Root + this.MacAddress + "/" + this.mi.Hash + "/Chunks/";

        File ficheiro = new File (tmpFolder);
        ArrayList<Chunk> res = new ArrayList<Chunk>();

        try {
            if(ficheiro.exists() && ficheiro.isDirectory()){
                Chunk f;
                for(Integer i : mfc){
                    f = new Chunk(Files.readAllBytes(Paths.get(tmpFolder + "/" + i + ".chunk")), i);
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
    public ArrayList<Integer> getMissingChunksIDs(){
        return new ArrayList<Integer>(this.mi.missingChunks);
    }


    public CompressedMissingChunksID getCompressedMissingChunksID(){

        //System.out.println(mfcGroup);
        ArrayList <Integer> mfcGroup = this.mi.missingChunks;

        int referenceID = mfcGroup.get(0);
        int currentID = referenceID;
        int counter = 0;
        ArrayList<Integer> toAdd = new ArrayList<Integer>();
        int toAddAux = 0;
        int pointer = 0;

        ArrayList<Byte> inc = new ArrayList<Byte>();

        mfcGroup.remove(0);

        int dif = Byte.MAX_VALUE - Byte.MIN_VALUE;

        for(int id : mfcGroup){
            while(currentID + dif < id){
                if(pointer == 8) {
                    toAdd.add(toAddAux);
                    //System.out.println("TO ADD => (int)" + toAddAux + " (byte)" + (byte)(toAddAux + Byte.MIN_VALUE));
                    pointer = 0;
                    toAddAux = 0;
                }
                pointer++;
                toAddAux *= 2;
                currentID +=  dif;
            }
            if(pointer == 8) {
                toAdd.add(toAddAux);
                //System.out.println("TO ADD => (int)" + toAddAux + " (byte)" + (byte)(toAddAux + Byte.MIN_VALUE));
                pointer = 0;
                toAddAux = 0;
            }
            pointer++;
            toAddAux *= 2;
            toAddAux++;
            //System.out.println("INC TO ADD => " + (byte)(id - currentID + Byte.MIN_VALUE));
            inc.add((byte)(id - currentID + Byte.MIN_VALUE));
            currentID = id;
            counter++;
        }
        if(pointer == 8) {
            toAdd.add(toAddAux);
            //System.out.println("TO ADD => (int)" + toAddAux + "\n\tpointer => " + pointer);
        }

        ArrayList<Byte> invertedToAdd = new ArrayList<Byte>();
        int invertedToAddAux;

        for(int b : toAdd){
            //System.out.println("ANTES => " + b);
            invertedToAddAux = 0;

            for (int i = 0; i < 8; i++) {
                invertedToAddAux *= 2;
                if (b % 2 == 1)
                    invertedToAddAux++;
                b /=2;
            }

            //System.out.println("DEPOIS => " + invertedToAddAux);
            //aux = invertedToAddAux;
            //auxb = (byte)(invertedToAddAux - Byte.MIN_VALUE);
            invertedToAdd.add((byte)(invertedToAddAux - Byte.MIN_VALUE));
        }

        if(pointer < 8){
            //System.out.println("TO ADD => (int)" + toAddAux + "\n\tpointer => " + pointer);
            invertedToAddAux = 0;

            for (int i = 0; i < pointer; i++) {
                invertedToAddAux *= 2;
                if (toAddAux % 2 == 1)
                    invertedToAddAux++;
                toAddAux /=2;

            }
            //aux = invertedToAddAux;
            //auxb = (byte)(invertedToAddAux - Byte.MIN_VALUE);
            invertedToAdd.add((byte)(invertedToAddAux - Byte.MIN_VALUE));
        }

        //System.out.println("INVERTED => " + aux + "\nIN BYTE => " + auxb);
        //System.out.println("\tINC SIZE => " + inc.size() + "\n\tTO ADD SIZE => " + toAdd.size() + "\n\tINVERTED TO ADO SIZ => " + invertedToAdd.size() + "\n\tPOINTER => " + pointer);
        byte[] toAddArray = new byte[invertedToAdd.size()];
        for(int i = 0; i < invertedToAdd.size(); i++)
            toAddArray [i] = invertedToAdd.get(i);

        byte[] incArray = new byte[inc.size()];
        for(int i = 0; i < inc.size(); i++) {
            //System.out.println("inc[" + i + "] = " + inc.get(i));
            incArray[i] = inc.get(i);
        }

        CompressedMissingChunksID structPointer = new CompressedMissingChunksID(referenceID, toAddArray, incArray);

        //System.out.println("TENHO " + counter +1 + " IDS");
        return structPointer;
    }

    public ArrayList<Integer> getIDsFromCompressedMissingChunksID(CompressedMissingChunksID cmcid){
        ArrayList<Integer> res = new ArrayList<Integer>();

        int currentID = cmcid.referenceID;
        byte[] toAdd = cmcid.toAdd;
        byte[] inc = cmcid.increments;

        int maxValue = Byte.MAX_VALUE - Byte.MIN_VALUE;

        res.add(currentID);
        int i = 0;
        int toAddAux = 0;
        int pointer;

        for (byte ta : toAdd){
            toAddAux = (int)ta - Byte.MIN_VALUE;
            //System.out.println("THIS HERE => " + toAddAux);

            for(pointer = 0; pointer < 8 && i < inc.length; pointer ++) {
                if (toAddAux % 2 == 0) {
                    //System.out.println("TO ADD ERA PAR");
                    currentID += maxValue;
                }
                else {
                    //System.out.println("TO ADD ERA IMPAR |" + "inc[" + i + "] = " + inc[i]);
                    currentID += ((int) inc[i++]) - Byte.MIN_VALUE;
                    res.add(currentID);
                }
                toAddAux /=2;
            }
/*            if(pointer == 8)
                System.out.println("POINTER");
            else
                System.out.println("INC SIZE | POINTER => " + pointer);*/

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
        return this.mi.missingChunks.size();
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
    private String sha_256_Chunks(ArrayList<Chunk> chunks, String alg){
        Hash h = new Hash(alg);

        for(Chunk c : chunks) {
            h.updateHash(c.getChunk());
        }

        return h.extractHash();
    }

    /*
     * Calculates the hash of the provided ArrayList<byte[]>
     * */
    private String sha_256_Bytes(ArrayList<byte[]> bytes, String alg){
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
        return hash.equals(sha_256_Chunks(chunks, alg));
    }

    /*
     * Checks if the provided ArrayList<byte[]> hash matches the provided hash
     * */
    public boolean checkHash_Bytes(ArrayList<byte[]> bytes, String hash, String alg){
        return hash.equals(sha_256_Bytes(bytes, alg));
    }

    /*
    * Deletes all Chunks that are saved in Memory as well the Data.DocumentMetaInfo File
    * */
    public void eraseChunks(){


        String path = this.Root + this.MacAddress + "/" + this.mi.Hash + "/";
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
}
