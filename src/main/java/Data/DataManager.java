package Data;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;


public class DataManager {
    private DataManagerMetaInfo dmMI;

    public HashMap <String, Document> documents;

    public HashMap<String, ChunkManager> messages;

    public DataManager(String Root){
        Root = folderPathNormalizer(Root);

        this.dmMI = new DataManagerMetaInfo(Root);

        this.documents = new HashMap<String, Document>();

        this.messages = new HashMap<String, ChunkManager>();

        writeDataManagerMetaInfoToRootFolder();
    }

    public DataManager(String Root, boolean fetch) {
        DataManagerMetaInfo dmmi = null;
        Root = folderPathNormalizer(Root);


        if (fetch) {
            dmmi = fetchDMMI(Root);
        }

        if(dmmi == null) {
            this.dmMI = new DataManagerMetaInfo(Root);

            this.documents = new HashMap<String, Document>();

            this.messages = new HashMap<String, ChunkManager>();

            writeDataManagerMetaInfoToRootFolder();
        }
        else {
            this.dmMI = dmmi;
            fetchChunkManagers(Root);

        }
    }
    /*
    * Constructs a Document object that uses a Data.ChunkManager to divide the file provided by the combination of localFilePath/filename
    * into chunks that can be found on the folder root/MacAddress/(FileHash)/Chunks.
    * After the construction of the Document object, it is registered the MacAddress that solicited the creation of this file
    * There's 2 versions, with and without the Hash Algorithm specification to use. By omission, the default is sha-256
    * */
    public void newDocument(String MacAddress, String localDocumentPath, String documentName, String hashAlgorithm, int maxDatagramSize){
        checkFolders(MacAddress);
        localDocumentPath = folderPathNormalizer(localDocumentPath);

        Document f = new Document(this.dmMI.Root, MacAddress, localDocumentPath, documentName, maxDatagramSize, hashAlgorithm);
        String hash = f.getHash();

        if(!(this.dmMI.macHashs.containsKey(MacAddress) && this.dmMI.macHashs.get(MacAddress).contains(hash))) {
            registerMacHashs(MacAddress, hash);
            //guardar o file
            this.documents.put(hash, f);

            //assinala que o ficheiro esta completo
            this.dmMI.isDocumentFull.put(hash, true);

            //guarda o nome do ficheiro
            this.dmMI.documentsNames.put(hash, documentName);

            updateDataManagerMetaInfoFile();
        }
    }
    public void newDocument(String MacAddress, String localDocumentPath, String documentName, int maxDatagramSize){
        checkFolders(MacAddress);
        localDocumentPath = folderPathNormalizer(localDocumentPath);

        Document f = new Document(this.dmMI.Root, MacAddress, localDocumentPath, documentName, maxDatagramSize, "SHA-256");
        String hash = f.getHash();

        if(!(this.dmMI.macHashs.containsKey(MacAddress) && this.dmMI.macHashs.get(MacAddress).contains(hash))) {
            registerMacHashs(MacAddress, hash);
            //guardar o file
            this.documents.put(hash, f);

            //assinala que o ficheiro esta completo
            this.dmMI.isDocumentFull.put(hash, true);

            //guarda o nome do ficheiro
            this.dmMI.documentsNames.put(hash, documentName);

            updateDataManagerMetaInfoFile();
        }
    }

    /*
    * Constructs a Document object that uses a Data.ChunkManager to divide the file provided by the combination of localFilePath/filename
     * into chunks that can be found on the folder root/MacAddress/(FileHash)/Chunks.
     * After the construction of the Document object, it is registered the MacAddress that solicited the creation of this file
     * There's 2 versions, with and without the Hash Algorithm specification to use. By omission, the default is sha-256
     * This constructor will limit the number of chunks in Ram to maxChunksLoadedAtaTime
    * */
    public void newDocument(String MacAddress, String localDocumentPath, String documentName, int maxDatagramSize, String hashAlgorithm, int maxChunksLoadedAtaTime){
        checkFolders(MacAddress);
        localDocumentPath = folderPathNormalizer(localDocumentPath);

        Document f = new Document(this.dmMI.Root, MacAddress, localDocumentPath, documentName, maxDatagramSize, hashAlgorithm,maxChunksLoadedAtaTime);
        String hash = f.getHash();

        if(!(this.dmMI.macHashs.containsKey(MacAddress) && this.dmMI.macHashs.get(MacAddress).contains(hash))) {
            registerMacHashs(MacAddress, hash);
            //guardar o file
            this.documents.put(hash, f);

            //assinala que o ficheiro esta completo
            this.dmMI.isDocumentFull.put(hash, true);

            //guarda o nome do ficheiro
            this.dmMI.documentsNames.put(hash, documentName);

            updateDataManagerMetaInfoFile();
        }
    }
    public void newDocument(String MacAddress, String localDocumentPath, String documentName, int maxDatagramSize, int maxChunksLoadedAtaTime){
        checkFolders(MacAddress);
        localDocumentPath = folderPathNormalizer(localDocumentPath);

        Document f = new Document(this.dmMI.Root, MacAddress, localDocumentPath, documentName, maxDatagramSize, "SHA-256",maxChunksLoadedAtaTime);
        String hash = f.getHash();

        if(!(this.dmMI.macHashs.containsKey(MacAddress) && this.dmMI.macHashs.get(MacAddress).contains(hash))) {
            registerMacHashs(MacAddress, hash);
            //guardar o file
            this.documents.put(hash, f);

            //assinala que o ficheiro esta completo
            this.dmMI.isDocumentFull.put(hash, true);

            //guarda o nome do ficheiro
            this.dmMI.documentsNames.put(hash, documentName);

            updateDataManagerMetaInfoFile();
        }
    }

    /*
    * Constructs a Document object for Documents to be received. It is needed to continuously add the chunks that are missing.
    * There's 2 versions, with and without the Hash Algorithm specification to use. By omission, the default is sha-256
    * */
    public void newDocument(String MacAddress, String hash, String hashAlgorithm, int numberOfChunks, String documentName) {
        checkFolders(MacAddress);

        if(!(this.dmMI.macHashs.containsKey(MacAddress) && this.dmMI.macHashs.get(MacAddress).contains(hash))) {

            Document f = new Document(this.dmMI.Root, MacAddress, hash, hashAlgorithm, numberOfChunks, documentName);

            registerMacHashs(MacAddress, hash);

            //guardar o file
            this.documents.put(hash, f);

            //assinala que o ficheiro esta completo
            this.dmMI.isDocumentFull.put(hash, false);

            //guarda o nome do ficheiro
            this.dmMI.documentsNames.put(hash, documentName);

            updateDataManagerMetaInfoFile();
        }
    }
    public void newDocument(String MacAddress, String hash, int numberOfChunks, String documentName) {
        checkFolders(MacAddress);

        if(!(this.dmMI.macHashs.containsKey(MacAddress) && this.dmMI.macHashs.get(MacAddress).contains(hash))) {

            Document f = new Document(this.dmMI.Root, MacAddress, hash, "SHA-256", numberOfChunks, documentName);

            registerMacHashs(MacAddress, hash);

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
    public void addChunksToDocument (ArrayList<Chunk> chunks, String MacAddress, String hash){
        Document f;

        if ((this.dmMI.macHashs.get(MacAddress).contains(hash)) && (!this.dmMI.isDocumentFull.get(hash))){
            f = this.documents.get(hash);
            f.addChunks(chunks);
            if(f.isFull())
                this.dmMI.isDocumentFull.put(hash, true);
            this.documents.put(hash, f); //???????? PReciso????
        }
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
    public void deleteDocument(String MacAddress, String hash){

        ArrayList <String> hashs = this.dmMI.macHashs.get(MacAddress);
        if(hashs.contains(hash)){
            this.documents.get(hash).delete();
            this.documents.remove(hash);
            this.dmMI.documentsNames.remove(hash);
            this.dmMI.isDocumentFull.remove(hash);


            hashs.remove(hash);
            this.dmMI.macHashs.put(MacAddress, hashs);
            updateDataManagerMetaInfoFile();
        }
    }

    /*
    * This will try to rebuild a Document from all the chunks that ar in root/MacAddress/hash/chunks.
    * It is required to all chunks to be in Memory
    * */
    public Boolean assembleDocument(String MacAddress, String Hash, String destinationPath){
        boolean res = false;

        destinationPath = folderPathNormalizer(destinationPath);

        ArrayList<String> documentHashs;
        Document d;

        System.out.println();
        if(this.dmMI.macHashs.containsKey(MacAddress)){
            documentHashs = this.dmMI.macHashs.get(MacAddress);

            if(documentHashs.contains(Hash) && this.dmMI.isDocumentFull.get(Hash)){
                d = this.documents.get(Hash);
                d.writeDocumentToFolder(destinationPath);

                res = true;
            }
        }

        return res;
    }



    /*
    * Constructs a ChunkManager object and creates all the chunks needed to represent the message byte[]. These chunks will
    * be present in root/MacAddress/hash/chunks.
    * There's 2 versions, with and without the Hash Algorithm specification to use. By omission, the default is sha-256
    * */
    public void newMessage(String MacAddress, byte[] info, int maxDatagramSize, String hashAlgorithm){
        checkFolders(MacAddress);

        ChunkManager cm = new ChunkManager(this.dmMI.Root, MacAddress, info, maxDatagramSize, hashAlgorithm);
        String hash = cm.getHash();

        if(!(this.dmMI.macHashs.containsKey(MacAddress) && this.dmMI.macHashs.get(MacAddress).contains(hash))) {

            registerMacHashs(MacAddress, hash);

            //guardar mensagens
            this.messages.put(hash, cm);

            //assinala que a mensagem esta completa
            this.dmMI.isMessageFull.put(cm.getHash(), true);

            updateDataManagerMetaInfoFile();
        }
    }
    public void newMessage(String MacAddress, byte[] info, int maxDatagramSize){
        checkFolders(MacAddress);

        ChunkManager cm = new ChunkManager(this.dmMI.Root, MacAddress, info, maxDatagramSize, "SHA-256");
        String hash = cm.getHash();

        if(!(this.dmMI.macHashs.containsKey(MacAddress) && this.dmMI.macHashs.get(MacAddress).contains(hash))) {

            registerMacHashs(MacAddress, hash);

            //guardar mensagens
            this.messages.put(hash, cm);

            //assinala que a mensagem esta completa
            this.dmMI.isMessageFull.put(cm.getHash(), true);

            updateDataManagerMetaInfoFile();
        }
    }

    /*
    * Constructs a ChunkManager object for Messages to be received. It is needed to continuously add the chunks that are missing.
    * There's 2 versions, with and without the Hash Algorithm specification to use. By omission, the default is sha-256
    * */
    public void newMessage(String MacAddress, String hash, String hashAlgorithm, int numberOfChunks){
        ChunkManager cm = new ChunkManager(this.dmMI.Root, MacAddress, hash, hashAlgorithm, numberOfChunks);

        if(!(this.dmMI.macHashs.containsKey(MacAddress) && this.dmMI.macHashs.get(MacAddress).contains(hash))) {

            registerMacHashs(MacAddress, hash);

            //guardar a mensagem
            this.messages.put(hash, cm);

            //assinala que a mensagem esta nao completa
            this.dmMI.isMessageFull.put(hash, false);

            updateDataManagerMetaInfoFile();
        }
    }
    public void newMessage(String MacAddress, String hash, int numberOfChunks){
        ChunkManager cm = new ChunkManager(this.dmMI.Root, MacAddress, hash, "SHA-256", numberOfChunks);

        if(!(this.dmMI.macHashs.containsKey(MacAddress) && this.dmMI.macHashs.get(MacAddress).contains(hash))) {

            registerMacHashs(MacAddress, hash);

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
    public void addChunksToMessage(String MacAddress, String Hash, ArrayList<Chunk> chunks){
        ChunkManager cm;

        if ((this.dmMI.macHashs.get(MacAddress).contains(Hash)) && (!this.dmMI.isMessageFull.get(Hash))){
            cm = this.messages.get(Hash);
            cm.addChunks(chunks);
            if(cm.getFull())
                this.dmMI.isMessageFull.put(Hash, true);
            this.messages.put(Hash, cm); //???????? PReciso????
        }
    }

    /*
    * Retrieves the information that represents a Document/Message in a byte[]
    * */
    public byte[] getInfoInByteArray(String MacAddress, String Hash){
        byte info[] = null;

        if(this.dmMI.macHashs.containsKey(MacAddress)) {
            if (this.messages.containsKey(Hash) && this.dmMI.isMessageFull.get(Hash)) {
                info = this.messages.get(Hash).getInfoInByteArray();
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
    public void deleteMessage(String MacAddress, String hash){
        ArrayList<String> hashs = this.dmMI.macHashs.get(MacAddress);

        if(hashs.contains(hash)){
            this.messages.get(hash).eraseChunks();
            this.messages.remove(hash);
            this.dmMI.isMessageFull.remove(hash);

            hashs.remove(hash);
            this.dmMI.macHashs.put(hash, hashs);

            updateDataManagerMetaInfoFile();
        }
    }


    /*
    * Register a Hash under the given MacAddress
    * If a MacAddress isn't registered yet, this will register it
    * */
    private void registerMacHashs(String MacAddress, String hash){
        // registar macaddress + info hash no hashmap
        ArrayList <String> hashs;
        if(this.dmMI.macHashs.containsKey(MacAddress)) {
            hashs = this.dmMI.macHashs.get(MacAddress);

        }
        else{
            hashs = new ArrayList<String>();
        }

        hashs.add(hash);
        this.dmMI.macHashs.put(MacAddress, hashs);
    }

    /*
    * Checks if a MacAddress Folder exists, if not it will be created
    * */
    private void checkFolders(String MacAddress){
        if(!this.dmMI.macHashs.containsKey(MacAddress)) {
            File macFolder = new File(this.dmMI.Root + "/" + MacAddress + "/");

            while (!macFolder.exists() && !macFolder.isDirectory() && !macFolder.mkdir()) ;
        }
    }

    /*
    * Writes the DataManagerMetaInfo to a Folder
    * */
    public void writeDataManagerMetaInfoToRootFolder(){
        String dataManagerMetaInfoFilePath = this.dmMI.Root + "DataManagerDocumentMeta.info";

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
        String dataManagerMetaInfoFilePath = Root + "DataManagerDocumentMeta.info";

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
        String dataManagerMetaInfoFilePath = this.dmMI.Root + "DataManagerDocumentMeta.info";

        File FileInfo = new File(this.dmMI.Root);

        if(FileInfo.exists()) {
            File documentMetaInfo = new File(dataManagerMetaInfoFilePath);
            while(!documentMetaInfo.delete());

            writeDataManagerMetaInfoToRootFolder();
        }
    }

    /*
    * Fetches and retrieves a DataManagerMetaInfo object from the root/ if present
    * */
    private DataManagerMetaInfo fetchDMMI(String Root){
        DataManagerMetaInfo dmmi = null;
        Object obj = null;

        String dataManagerMetaInfoFilePath = Root + "DataManagerDocumentMeta.info";

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
        ArrayList <String> hashs;
        ArrayList <ChunkManager> chunkManagers = new ArrayList<ChunkManager>();
        ChunkManager cm;
        Document d;

        this.documents = new HashMap<String, Document>();
        this.messages = new HashMap<String, ChunkManager>();

        for(String mac : this.dmMI.macHashs.keySet()){
            hashs = this.dmMI.macHashs.get(mac);

            for(String hash : hashs){
                cm = new ChunkManager(root, mac);

                cm.readDocumentMetaInfoFromFile(hash);

                if(this.dmMI.documentsNames.containsKey(hash)){
                    d = new Document(root, mac, this.dmMI.documentsNames.get(hash), cm);
                    this.documents.put(hash, d);
                }
                else{
                    this.messages.put(hash, cm);
                }
            }
        }
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
}
