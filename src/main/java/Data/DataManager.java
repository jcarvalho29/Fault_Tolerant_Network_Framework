package Data;

import Messages.ChunkMessage;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;


public class DataManager {
    private DataManagerMetaInfo dmMI;

    public HashMap <String, Document> documents;

    public HashMap<String, ChunkManager> messages;


    public DataManager(String Root){
        Root = folderPathNormalizer(Root + "Data/");

        this.dmMI = new DataManagerMetaInfo(Root);

        this.documents = new HashMap<String, Document>();

        this.messages = new HashMap<String, ChunkManager>();

        writeDataManagerMetaInfoToRootFolder();
    }

    public DataManager(String Root, boolean fetch) {
        DataManagerMetaInfo dmmi = null;
        Root = folderPathNormalizer(Root + "Data/");


        if (fetch) {
            dmmi = fetchDMMI(Root);
        }

        if(dmmi == null) {
            System.out.println("DMMI NULL");
            createDataFolders(Root);
            System.out.println("CREATED DATA FOLDER");
            this.dmMI = new DataManagerMetaInfo(Root);

            this.documents = new HashMap<String, Document>();

            this.messages = new HashMap<String, ChunkManager>();

            writeDataManagerMetaInfoToRootFolder();
        }
        else {
            this.dmMI = dmmi;
            System.out.println("RESTORED DMMI");

            fetchChunkManagers(Root);
        }
    }

    private void createDataFolders(String Root) {

        String path = Root;
        System.out.println(path);
        File root = new File(path);
        while(!root.exists() && !root.isDirectory() && !root.mkdir());
    }

    /*
    * Constructs a Document object that uses a Data.ChunkManager to divide the file provided by the combination of localFilePath/filename
    * into chunks that can be found on the folder root/MacAddress/(FileHash)/Chunks.
    * After the construction of the Document object, it is registered the MacAddress that solicited the creation of this file
    * There's 2 versions, with and without the Hash Algorithm specification to use. By omission, the default is sha-256
    * */
    public String newDocument(String localDocumentPath, String hashAlgorithm, int maxDatagramSize){
        String hash = null;

        String[] pathSplited = localDocumentPath.split("/");
        String documentName = pathSplited[pathSplited.length-1];

        localDocumentPath = folderPathNormalizer(localDocumentPath);

        Document f = new Document(this.dmMI.Root, localDocumentPath, documentName, maxDatagramSize, hashAlgorithm);
        hash = f.cm.mi.Hash;

        if(!(this.dmMI.cmHashs.contains(hash))) {
            registerHash(hash);
            //guardar o file
            this.documents.put(hash, f);

            //assinala que o ficheiro esta completo
            this.dmMI.isDocumentFull.put(hash, true);

            //guarda o nome do ficheiro
            this.dmMI.documentsNames.put(hash, documentName);

            updateDataManagerMetaInfoFile();
        }

        return hash;
    }
    public String newDocument(String localDocumentPath, int maxDatagramSize){
        String hash = null;

        String[] pathSplited = localDocumentPath.split("/");
        String documentName = pathSplited[pathSplited.length-1];

        localDocumentPath = folderPathNormalizer(localDocumentPath);

        Document f = new Document(this.dmMI.Root, localDocumentPath, documentName, maxDatagramSize, "SHA-256");
        hash = f.cm.mi.Hash;

        if(!(this.dmMI.cmHashs.contains(hash))) {
            registerHash(hash);
            //guardar o file
            this.documents.put(hash, f);

            //assinala que o ficheiro esta completo
            this.dmMI.isDocumentFull.put(hash, true);

            //guarda o nome do ficheiro
            this.dmMI.documentsNames.put(hash, documentName);

            updateDataManagerMetaInfoFile();
        }

        return hash;
    }

    /*
    * Constructs a Document object that uses a Data.ChunkManager to divide the file provided by the combination of localFilePath/filename
     * into chunks that can be found on the folder root/MacAddress/(FileHash)/Chunks.
     * After the construction of the Document object, it is registered the MacAddress that solicited the creation of this file
     * There's 2 versions, with and without the Hash Algorithm specification to use. By omission, the default is sha-256
     * This constructor will limit the number of chunks in Ram to maxChunksLoadedAtaTime
    * */
    public String newDocument(String localDocumentPath, int maxDatagramSize, String hashAlgorithm, int maxChunksLoadedAtaTime){
        String hash = null;

        String[] pathSplited = localDocumentPath.split("/");
        String documentName = pathSplited[pathSplited.length-1];

        localDocumentPath = folderPathNormalizer(localDocumentPath);

        Document f = new Document(this.dmMI.Root, localDocumentPath, documentName, maxDatagramSize, hashAlgorithm,maxChunksLoadedAtaTime);
        hash = f.cm.mi.Hash;

        if(!(this.dmMI.cmHashs.contains(hash))) {
            registerHash(hash);
            //guardar o file
            this.documents.put(hash, f);

            //assinala que o ficheiro esta completo
            this.dmMI.isDocumentFull.put(hash, true);

            //guarda o nome do ficheiro
            this.dmMI.documentsNames.put(hash, documentName);

            updateDataManagerMetaInfoFile();
        }

        return hash;
    }
    public String newDocument(String localDocumentPath, int maxDatagramSize, int maxChunksLoadedAtaTime){
        String hash = null;

        String[] pathSplited = localDocumentPath.split("/");
        String documentName = pathSplited[pathSplited.length-1];

        localDocumentPath = folderPathNormalizer(localDocumentPath);

        Document f = new Document(this.dmMI.Root, localDocumentPath, documentName, maxDatagramSize, "SHA-256",maxChunksLoadedAtaTime);
        hash = f.cm.mi.Hash;

        if(!(this.dmMI.cmHashs.contains(hash))) {
            registerHash(hash);
            //guardar o file
            this.documents.put(hash, f);

            //assinala que o ficheiro esta completo
            this.dmMI.isDocumentFull.put(hash, true);

            //guarda o nome do ficheiro
            this.dmMI.documentsNames.put(hash, documentName);

            updateDataManagerMetaInfoFile();
        }

        return hash;
    }

    /*
    * Constructs a Document object for Documents to be received. It is needed to continuously add the chunks that are missing.
    * There's 2 versions, with and without the Hash Algorithm specification to use. By omission, the default is sha-256
    * */
    public void newDocument(String hash, String hashAlgorithm, int numberOfChunks, String documentName) {

        if(!(this.dmMI.cmHashs.contains(hash))) {

            Document f = new Document(this.dmMI.Root, hash, hashAlgorithm, numberOfChunks, documentName);

            registerHash(hash);

            //guardar o file
            this.documents.put(hash, f);

            //assinala que o ficheiro esta completo
            this.dmMI.isDocumentFull.put(hash, false);

            //guarda o nome do ficheiro
            this.dmMI.documentsNames.put(hash, documentName);

            updateDataManagerMetaInfoFile();
        }
    }
    public void newDocument(String hash, int datagramMaxSize, int numberOfChunks, String documentName) {

        if(!this.dmMI.cmHashs.contains(hash)) {
            System.out.println("ITS A NEW DOCUMENT");
            Document f = new Document(this.dmMI.Root, hash, datagramMaxSize, numberOfChunks, documentName, "SHA-256");

            registerHash(hash);

            //guardar o file
            this.documents.put(hash, f);

            //assinala que o ficheiro esta completo
            this.dmMI.isDocumentFull.put(hash, false);

            //guarda o nome do ficheiro
            this.dmMI.documentsNames.put(hash, documentName);

            updateDataManagerMetaInfoFile();
        }
    }

    /*
    * Adds the provided chunks to the Document that the provided MacAddress/Hash identify. This is intended to be used with the
    * Documents that are being received and not all chunks are present in Memory.
    * */
    private boolean addChunksToDocument (String hash, ChunkMessage[] chunks){
        Document f;
        boolean full = false;

        if ((this.dmMI.cmHashs.contains(hash)) && (this.documents.containsKey(hash)) && (!this.dmMI.isDocumentFull.get(hash))) {
            f = this.documents.get(hash);

            full = f.addChunks(chunks);
            if (full)
                this.dmMI.isDocumentFull.put(hash, true);
            this.documents.put(hash, f); //???????? PReciso????
        }

        return full;
    }

    /*
    * Deletes all Documents that correspond to the the hash that represents the Document object.
    * Updates the structure macHashs
    * Updates the document hashmap
     * updates the dmMI.documentsNames hashmap
     * updates the dmMI.isDocumentFull hashmap
    * Removes the corresponding entry from the Documents entry
    * Updates the DataManagerMetaInfo file
    * */
    public void deleteDocument(String hash){

        if(this.dmMI.cmHashs.contains(hash)){
            if(this.documents.containsKey(hash)) {
                this.documents.get(hash).delete();
                this.documents.remove(hash);
                this.dmMI.documentsNames.remove(hash);
                this.dmMI.isDocumentFull.remove(hash);


                this.dmMI.cmHashs.remove(hash);
                updateDataManagerMetaInfoFile();
            }
        }
    }

    /*
    * This will try to rebuild a Document from all the chunks that ar in root/MacAddress/hash/chunks.
    * It is required to all chunks to be in Memory
    * */
    public Boolean assembleDocument(String Hash, String destinationPath){
        boolean res = false;

        destinationPath = folderPathNormalizer(destinationPath);

        Document d;
        System.out.println("Hash " + Hash);
        System.out.println(this.dmMI.cmHashs);
        System.out.println(this.dmMI.cmHashs.contains(Hash));
        System.out.println(this.documents);
        System.out.println(this.documents.containsKey(Hash));
        System.out.println(this.dmMI.isDocumentFull.get(Hash));

        if(this.dmMI.cmHashs.contains(Hash)){//CHECK THIS
            System.out.println("CMHASH HAS HASH");
            if(this.documents.containsKey(Hash) && this.dmMI.isDocumentFull.get(Hash)){//CHECK THIS
                System.out.println("DOCUMENTS HAS HASH AND DOCUMENT IS FULL");
                d = this.documents.get(Hash);
                d.writeDocumentToFolder(destinationPath);

                res = true;
            }
            else{
                if(this.documents.containsKey(Hash))
                    System.out.println("    DOCUMENTS HAS HASH BUT DOCUMENT IS not FULL");
                else
                    System.out.println("    DOCUMENTS DOESNT HAVE HASH");
            }
        }

        return res;
    }



    /*
    * Constructs a ChunkManager object and creates all the chunks needed to represent the message byte[]. These chunks will
    * be present in root/MacAddress/hash/chunks.
    * There's 2 versions, with and without the Hash Algorithm specification to use. By omission, the default is sha-256
    * */
    public String newMessage(byte[] info, int maxDatagramSize, String hashAlgorithm){
        String hash = null;

        ChunkManager cm = new ChunkManager(this.dmMI.Root, info, maxDatagramSize, hashAlgorithm);
        hash = cm.mi.Hash;

        if(!(this.dmMI.cmHashs.contains(hash))) {

            registerHash(hash);

            //guardar mensagens
            this.messages.put(hash, cm);

            //assinala que a mensagem esta completa
            this.dmMI.isMessageFull.put(cm.mi.Hash, true);

            updateDataManagerMetaInfoFile();
        }

        return hash;
    }
    public String newMessage(byte[] info, int maxDatagramSize){
        String hash = null;
        ChunkManager cm = new ChunkManager(this.dmMI.Root, info, maxDatagramSize, "SHA-256");
        hash = cm.mi.Hash;

        if(!(this.dmMI.cmHashs.contains(hash))) {

            registerHash(hash);

            //guardar mensagens
            this.messages.put(hash, cm);

            //assinala que a mensagem esta completa
            this.dmMI.isMessageFull.put(cm.mi.Hash, true);

            updateDataManagerMetaInfoFile();
        }

        return  hash;
    }

    /*
    * Constructs a ChunkManager object for Messages to be received. It is needed to continuously add the chunks that are missing.
    * There's 2 versions, with and without the Hash Algorithm specification to use. By omission, the default is sha-256
    * */
    public void newMessage(String hash, String hashAlgorithm, int  datagramMaxSize, int numberOfChunks){
        ChunkManager cm = new ChunkManager(this.dmMI.Root, datagramMaxSize, hash, hashAlgorithm, numberOfChunks);

        if(!(this.dmMI.cmHashs.contains(hash))) {

            registerHash(hash);

            //guardar a mensagem
            this.messages.put(hash, cm);

            //assinala que a mensagem esta nao completa
            this.dmMI.isMessageFull.put(hash, false);

            updateDataManagerMetaInfoFile();
        }
    }
    public void newMessage(String hash, int datagramMaxSize, int numberOfChunks){
        ChunkManager cm = new ChunkManager(this.dmMI.Root, datagramMaxSize, hash, "SHA-256", numberOfChunks);

        if(!(this.dmMI.cmHashs.contains(hash))) {

            registerHash(hash);

            //guardar a mensagem
            this.messages.put(hash, cm);

            //assinala que a mensagem esta nao completa
            this.dmMI.isMessageFull.put(hash, false);

            updateDataManagerMetaInfoFile();
        }
    }

    /*
     * Adds the provided chunks to the ChunkManager that the provided MacAddress/Hash identify. This is intended to be used with the
     * ChunkManager that are being received and not all chunks are present in Memory.
     * */
    private boolean addChunksToMessage(String Hash, ChunkMessage[] chunks){
        ChunkManager cm;
        boolean full = false;

        if ((this.dmMI.cmHashs.contains(Hash)) && (!this.dmMI.isMessageFull.get(Hash))){
            cm = this.messages.get(Hash);
            full = cm.addChunks(chunks);
            if(cm.mi.full)
                this.dmMI.isMessageFull.put(Hash, true);
            this.messages.put(Hash, cm); //???????? PReciso????
        }

        return full;
    }


    /*
    * Tries to add the specified chunks to a ChunkManager defined by the provided Hash
    * */
    public boolean addChunks(String Hash, ChunkMessage[] chunks) {
        boolean full = false;
        if (this.documents.containsKey(Hash)) {
            full = addChunksToDocument(Hash, chunks);
            System.out.println(chunks.length + " CHUNKS ADDED");
        }
        else {
            if (this.messages.containsKey(Hash)) {
                full = addChunksToMessage(Hash, chunks);
            }
        }

        return full;
    }

    /*
    * Retrieves the information that represents a Document/Message in a byte[]
    * */
    public byte[] getInfoInByteArray(String Hash){
        byte info[] = null;

        if(this.dmMI.cmHashs.contains(Hash)) {
            if (this.messages.containsKey(Hash) && this.dmMI.isMessageFull.get(Hash)) {
                info = this.messages.get(Hash).getInfoInByteArray();
            }
            else{
                info = this.documents.get(Hash).cm.getInfoInByteArray();
            }
        }


        return info;
    }

    /*
     * Deletes all Messages that correspond to the the hash.
     * Updates the structure macHashs
     * Removes the entry from dmMI.isMessageFull
     * Removes the corresponding entry from the Messages entry
     * Updates the DataManagerMetaInfo file
     * */
    public void deleteMessage(String hash){

        if(this.dmMI.cmHashs.contains(hash)){
            this.messages.get(hash).eraseChunks();
            this.messages.remove(hash);
            this.dmMI.isMessageFull.remove(hash);

            this.dmMI.cmHashs.remove(hash);
            updateDataManagerMetaInfoFile();
        }
    }


    /*
    * Register a Hash under the given MacAddress
    * If a MacAddress isn't registered yet, this will register it
    * */
    private void registerHash(String hash){
        // registar macaddress + info hash no hashmap
        ArrayList <String> hashs;
        if(!this.dmMI.cmHashs.contains(hash)) {
            this.dmMI.cmHashs.add(hash);
            System.out.println("REGISTERED HASH " + hash);
        }
        else
            System.out.println("    DID NOT REGISTER HASH");

    }

    /*
    * Writes the DataManagerMetaInfo to a Folder
    * */
    public void writeDataManagerMetaInfoToRootFolder(){
        String dataManagerMetaInfoFilePath = this.dmMI.Root + "DataManagerMeta.info";

        File dmMIInfo = new File(dataManagerMetaInfoFilePath);


        FileOutputStream fileOut = null;
        try {
            fileOut = new FileOutputStream(dmMIInfo);
            ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
            objectOut.writeObject(this.dmMI);
            objectOut.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
    * Reads and retrieves an Object from the root folder that corresponds to a DataManagerMetaInfo
    * */
    private Object readDataManagerMetaInfoFromFile(String Root){
        String dataManagerMetaInfoFilePath = Root + "DataManagerMeta.info";

        File FileInfo = new File(Root);
        Object obj = null;

        if(FileInfo.exists()) {
            FileInputStream fileIn = null;

            try {
                fileIn = new FileInputStream(dataManagerMetaInfoFilePath);
                ObjectInputStream objectIn = new ObjectInputStream(fileIn);

                obj = objectIn.readObject();

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        return obj;
    }

    /*
     * Deletes the current DataManagerMetaInfo File in root/ and writes the new DataManagerMetaInfo to the same path
     */
    private void updateDataManagerMetaInfoFile(){
        String dataManagerMetaInfoFilePath = this.dmMI.Root + "DataManagerMeta.info";

        File FileInfo = new File(this.dmMI.Root);

        if(FileInfo.exists()) {
            File dataManagerMetaInfo = new File(dataManagerMetaInfoFilePath);
            while(!dataManagerMetaInfo.delete());

            writeDataManagerMetaInfoToRootFolder();
        }
    }

    /*
    * Fetches and retrieves a DataManagerMetaInfo object from the root/ if present
    * */
    private DataManagerMetaInfo fetchDMMI(String Root){
        Object obj = null;

        String dataManagerMetaInfoFilePath = Root + "DataManagerMeta.info";

        File dataManagerMetaInfoFile = new File(dataManagerMetaInfoFilePath);

        if (dataManagerMetaInfoFile.exists()) {
            obj = readDataManagerMetaInfoFromFile(Root);
        }

        return  (DataManagerMetaInfo) obj;
    }

    /*
    * Fetches and reconstructs the ChunkManagers structures (documents/Messages)
    * */
    private void fetchChunkManagers(String root) {
        ChunkManager cm;
        Document d;

        this.documents = new HashMap<String, Document>();
        this.messages = new HashMap<String, ChunkManager>();

        for(String hash : this.dmMI.cmHashs){
            cm = new ChunkManager(root);

            cm.readDocumentMetaInfoFromFile(hash);

            if(this.dmMI.documentsNames.containsKey(hash)){
                d = new Document(root, this.dmMI.documentsNames.get(hash), cm);
                this.documents.put(hash, d);
            }
            else{
                this.messages.put(hash, cm);
            }
        }
    }

    /*
    * Indicates if said information can be sent
    * */
    public boolean isReadyToBeSent(String hash){
        boolean res = false;

        if((this.dmMI.isMessageFull.containsKey(hash) && this.dmMI.isMessageFull.get(hash)) || (this.dmMI.isDocumentFull.containsKey(hash) && this.dmMI.isDocumentFull.get(hash)))
            res = true;

        return res;
    }

    /*
    * Normalizes the given path
    * */
    private String folderPathNormalizer(String path){

        path = path + '/';
        int charIndex;

        while((charIndex = path.indexOf("//")) != -1)
            path = path.substring(0, charIndex) + path.substring(charIndex+1);


        return path;
    }

    /*
    * Checks the existence of a ChunkManager
    * */
    public boolean hasChunkManager(String Hash){
        boolean res = false;

        if(this.documents.containsKey(Hash) || this.messages.containsKey(Hash))
            res = true;

        return res;
    }

    public ArrayList<CompressedMissingChunksID> getCompressedMissingChunkIDs(String Hash, int maxSize){

        ChunkManager cm = null;

        if(this.documents.containsKey(Hash))
            cm = this.documents.get(Hash).cm;
        else {
            if (this.messages.containsKey(Hash))
                cm = this.messages.get(Hash);
        }

        ArrayList<CompressedMissingChunksID> res = null;
        if(cm != null){
            res = cm.getCompressedMissingChunksID(maxSize);
        }


        return res;
    }

    public void changeIsFullEntry(String Hash, boolean full){

        if(this.dmMI.isDocumentFull.containsKey(Hash)) {
            this.dmMI.isDocumentFull.remove(Hash);
            this.dmMI.isDocumentFull.put(Hash, full);
            updateDataManagerMetaInfoFile();
        }
        else
            if(this.dmMI.isMessageFull.containsKey(Hash)){
                this.dmMI.isMessageFull.remove(Hash);
                this.dmMI.isMessageFull.put(Hash, full);
                updateDataManagerMetaInfoFile();
            }
    }

    public boolean isChunkManagerFull(String Hash) {
        boolean res = false;

        if(this.documents.containsKey(Hash)) {
            res = this.dmMI.isDocumentFull.get(Hash);
        }
        else {
            if (this.messages.containsKey(Hash))
                res = this.dmMI.isMessageFull.get(Hash);
        }

        return res;

    }
}
