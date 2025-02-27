package tictactoe.server;

import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import tictactoe.network.CustomSocket;
import tictactoe.network.NetworkMessage;
import tictactoe.network.ProtocolAction;
import tictactoe.Text;
import tictactoe.grid.Grid;
import tictactoe.grid.Grid2D;
import tictactoe.grid.Grid3D;
import tictactoe.grid.exceptions.PositionInvalidException;
import tictactoe.grid.exceptions.PositionUsedException;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Random;

/**
 * Class to create a server. It will manage two clients once they are connected and allow them to play a tic-tac-toe game.
 * @author Bernard Alban
 * @author Blumet Thomas
 * @author Burdairon Florian
 * @version 1
 */
public class Server extends Thread {
    /**
     * Integer to keep the port of the connexion.
     */
    private final int port;

    /**
     * The first client to connect to the server, he will choose the grid dimensions.
     */
    private CustomSocket client1;
    /**
     * The second client to connect to the server.
     */
    private CustomSocket client2;

    /**
     * Boolean to know whose turn it is.
     */
    private boolean isClient1Turn;

    /**
     * The grid of the game, at its actual state.
     */
    private Grid grid = null;

    /**
     * Store the position of a pawn and the role("X" or "O") of the player who plays this pawn
     */
    private String[] lastPlaceTurn = new String[2];

    /**
     * The storage of the player who stays before the unexpected disconnection
     */
    private String lastPlayer = "X";

    /**
     * The grid which is serialized when a player  the party
     */
    private String serializedGrid = null;

    /**
     * The path of the save file
     */
    private String savePath = null;
    /**
     * Creates a local server with the default port (9876) open.
     */
    public Server () {
        this(9876);
    }

    /**
     * Creates a local server with the chosen port open.
     * @param port the port to open for the server.
     */
    public Server (int port) {
        this.port = port;
        // Start the server and wait for first client
    }

    /**
     * Main function of the server, manage the connexion and dimensions selection.
     * TODO: Implements a loop if players want to play again on the same network mode.
     */
    @Override
    public void run(){
        try {
            ServerSocket server = new ServerSocket(port);
            //System.out.println("En attente de joueur...");

            client1 = new CustomSocket(server.accept(), false);
            //System.out.println("Joueur 1 connecté");
            client2 = new CustomSocket(server.accept(), false);
            //System.out.println("Joueur 2 connecté");
            
            
            //System.out.println("Dimensions sélectionnées");
            boolean isNetWorkError;
            String path;
            if(System.getProperty("os.name").toUpperCase().contains("WIN")){
                path = System.getenv("APPDATA") + "/TicTacToe";
            }
            else{
                path = System.getenv(("HOME")) + "/.tictactoe";
            }
            File file = new File(path);
            if (file.isDirectory()){
                File[] files = file.listFiles();
                if (files != null){
                    boolean isSave = false;
                    for (File f : files){
                        if(f.isDirectory()){
                            isSave = true;
                        }
                    }
                    if(isSave) isNetWorkError = resumeGame();
                    else isNetWorkError = selectDimensions();
                }
                else isNetWorkError = selectDimensions();
            }
            else isNetWorkError = selectDimensions();

            startGame();

            boolean isEndGame = false;

            NetworkMessage msgClient1 = new NetworkMessage(ProtocolAction.NONE);
            NetworkMessage msgClient2 = new NetworkMessage(ProtocolAction.NONE);
            String[] paramClient1 = {};
            String[] paramClient2 = {};
            boolean isMsgClient1Used = true;
            boolean isMsgClient2Used = true;
            String existSavePath = savePath != null ? "1" : "0";

            while(!isEndGame && !isNetWorkError){
                if(isMsgClient1Used){
                    msgClient1 = client1.read();
                    paramClient1 = msgClient1.getParameters();
                }
                if(isMsgClient2Used){
                    msgClient2 = client2.read();
                    paramClient2 = msgClient2.getParameters();
                }
                if(msgClient1.getProtocolAction() == ProtocolAction.NetworkError || msgClient2.getProtocolAction() == ProtocolAction.NetworkError){
                    CustomSocket client = (msgClient1.getProtocolAction() == ProtocolAction.NetworkError) ? client2 : client1;
                    networkError(client, existSavePath);
                    if (msgClient1.getProtocolAction() == ProtocolAction.NetworkError) {
                        isMsgClient1Used = false;
                        isMsgClient2Used = true;
                    }
                    else{
                        isMsgClient1Used = true;
                        isMsgClient2Used = false;
                    }
                }
                if(msgClient1.getProtocolAction() == ProtocolAction.Quit || msgClient2.getProtocolAction() == ProtocolAction.Quit){
                    if (msgClient1.getProtocolAction() == ProtocolAction.Quit) {
                        if(paramClient1[0].equals("0")) {
                            quit(client1);
                            if(client2.isConnected()) networkError(client2, existSavePath);
                        }
                        else {
                            quit(client1);
                            save(paramClient1[1]);
                            if(client2.isConnected()) quit(client2);
                        }
                        isMsgClient1Used = true;
                        isMsgClient2Used = client2.isConnected();
                    }
                    else {
                        if(paramClient2[0].equals("0")) {
                            quit(client2);
                            if(client1.isConnected()) networkError(client1, existSavePath);
                        }
                        else {
                            quit(client2);
                            save(paramClient2[1]);
                            if(client1.isConnected()) quit(client1);
                        }
                        isMsgClient1Used = client1.isConnected();
                        isMsgClient2Used = true;
                    }
                    if(!client1.isConnected() && !client2.isConnected()) isNetWorkError = true;
                }
                if(msgClient1.getProtocolAction() == ProtocolAction.Place && msgClient2.getProtocolAction() == ProtocolAction.WaitMessage){
                    verification(client1, paramClient1[0], paramClient1[1].charAt(0));
                    isMsgClient1Used = true;
                    isMsgClient2Used = false;
                }
                if(msgClient2.getProtocolAction() == ProtocolAction.Place && msgClient1.getProtocolAction() == ProtocolAction.WaitMessage){
                    verification(client2, paramClient2[0], paramClient2[1].charAt(0));
                    isMsgClient1Used = false;
                    isMsgClient2Used = true;
                }
                if(msgClient1.getProtocolAction() == ProtocolAction.Confirmation && msgClient2.getProtocolAction() == ProtocolAction.WaitMessage){
                    isEndGame = play(client1, client2);
                    isMsgClient1Used = true;
                    isMsgClient2Used = true;
                }
                if(msgClient2.getProtocolAction() == ProtocolAction.Confirmation && msgClient1.getProtocolAction() == ProtocolAction.WaitMessage){
                    isEndGame = play(client2, client1);
                    isMsgClient1Used = true;
                    isMsgClient2Used = true;
                }
            }
            if(isEndGame){
                msgClient1 = client1.read();
                msgClient2 = client2.read();
                if(msgClient1.getProtocolAction() == ProtocolAction.WaitMessage && msgClient2.getProtocolAction() == ProtocolAction.WaitMessage){
                    client1.send(new NetworkMessage(ProtocolAction.Quit));
                    client2.send(new NetworkMessage(ProtocolAction.Quit));
                    client1.disconnect();
                    client2.disconnect();
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Returns the ip address of the server to be able to connect to it locally (on the same network).
     * @return a String which is the ip address.
     */
    public String getIpAddress(){
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "0.0.0.0";
        }
    }

    /**
     * Function that send a message to ask client1 to choose the size and the dimension of the grid and process the answer.
     */
    public boolean selectDimensions(){
        //Sending message to ask client1 to select dimensions");
        NetworkMessage msg = new NetworkMessage(ProtocolAction.SelectDimensions);
        client1.send(msg);

        boolean isDimensionSelected = false;

        //Loop if the answer is not correct and wait for another answer
        while(!isDimensionSelected){
            NetworkMessage answer;
            try{
                answer = client1.read();
            }
            //Problem with reading the answer
            catch (Exception e){
                //Set answer to null
                answer = new NetworkMessage(ProtocolAction.NONE);
                System.out.println("Error on reading");
            }
            ProtocolAction action = answer.getProtocolAction();

            //If the answer is a message with the action AnswerDimension
            if (action == ProtocolAction.AnswerDimensions) {
                String[] parameters = answer.getParameters();
                //If the answer have 2 parameters and the size is greater than 2
                try{
                    if(parameters.length == 2 && Integer.parseInt(parameters[0]) > 2){
                        setGrid(Integer.parseInt(parameters[0]),Integer.parseInt(parameters[1]));
                        isDimensionSelected = true;
                    }
                }
                catch (Exception ignored){
                    System.out.println(Text.error("s"));
                }
            }
            if(action == ProtocolAction.NetworkError) {
                System.out.println(Text.error("n"));
                return true;
            }
            //If the client1 didn't answer correctly the server send an error message
            if(!isDimensionSelected) {
                error(client1, "0");
            }
        }
        return false;
    }

    /**
     * @param width grid's width
     * @param dimension grid's dimension(2d or 3d)
     * @throws Exception throw if grid format is invalid
     */
    private void setGrid(int width,int dimension) throws Exception {
        if(dimension == 2){
            grid = new Grid2D(width);
        }
        //If the dimension is 3D
        else if(dimension == 3){
            grid = new Grid3D(width);
        }
        else{
            throw new Exception("Grid format invalid !");
        }
    }

    /**
     * Run the game from beginning to end, without any regards to what is extern of the game
     */
    public void startGame(){
        Random rand = new Random();
        NetworkMessage msgClient1;
        NetworkMessage msgClient2;
        String[] param1;
        String[] param2;

        if(serializedGrid != null){
            param1 = new String[5];
            param2 = new String[5];
            param1[4] = serializedGrid;
            param2[4] = serializedGrid;
        }
        else{
            param1 = new String[4];
            param2 = new String[4];
        }

        if(serializedGrid == null) isClient1Turn = rand.nextBoolean();

        if(isClient1Turn){
            param1[0] = "X";
            param2[0] = "O";
        }
        else {
            param1[0] = "O";
            param2[0] = "X";
        }
        param1[1] = "X";
        param2[1] = "X";

        param1[1] = "X";
        param2[1] = "X";

        if(serializedGrid != null){
            String nextPlayer = (lastPlayer.equalsIgnoreCase("X") ? "O" : "X");
            param1[1] = nextPlayer;
            param2[1] = nextPlayer;
        }

        if(grid.getClass() == Grid3D.class){
            param1[2] = "3";
            param2[2] = "3";
        }
        else{
            param1[2] = "2";
            param2[2] = "2";
        }

        param1[3] = Integer.toString(grid.getSize());
        param2[3] = Integer.toString(grid.getSize());

        msgClient1 = new NetworkMessage(ProtocolAction.StartGame, param1);
        msgClient2 = new NetworkMessage(ProtocolAction.StartGame, param2);
        client1.send(msgClient1);
        client2.send(msgClient2);
    }

    /**
     * Function which check the correct placement of a pawn
     * @param client the client that sent the placement
     * @param position the position of the pawn
     * @param role the role of the client
     */
    public void verification(CustomSocket client, String position, char role){
        try {
            if(!grid.isCellUsed(position)){
                lastPlaceTurn[0] = position;
                lastPlaceTurn[1] = Character.toString(role);
                client.send(new NetworkMessage(ProtocolAction.AskConfirmation));
            }
            else{
                error(client, "1");
            }
        } catch (PositionInvalidException e) {
            error(client, "2");
        }
    }

    /**
     * Function dedicated to manage the game: send the message to place pawns to players in turn,
     * execute the verification if the placement is lawful or not and if there's a winner
     * @param client1 the client that played
     * @param client2 the opponent player
     * @return true if there is a winner
     */
    public boolean play(CustomSocket client1, CustomSocket client2){
        try {
            ProtocolAction action;
            boolean isWinner = grid.place(lastPlaceTurn[0], lastPlaceTurn[1].charAt(0));
            lastPlayer = lastPlaceTurn[1];
            int nbCellFree = grid.getRemainingCells();
            if (isWinner || nbCellFree == 0){
                String[] param = new String[3];
                action = ProtocolAction.EndGame;
                param[0] = lastPlaceTurn[0];
                param[1] = lastPlaceTurn[1];
                param[2] = "0";
                if (!isWinner) param[2] = "1";
                client1.send(new NetworkMessage(action, param));
                Thread.sleep(200);
                client2.send(new NetworkMessage(action, param));
                if(savePath != null){
                    File saveDirectory = new File(savePath);
                    FileUtils.deleteDirectory(saveDirectory);
                }
                return true;
            }
            else{
                String[] param;
                param = lastPlaceTurn;
                client1.send(new NetworkMessage(ProtocolAction.Validate, param));
                Thread.sleep(500);
                client2.send(new NetworkMessage(ProtocolAction.Play, param));
            }
        } catch (PositionUsedException | PositionInvalidException | InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    /**
     * Function which manage every type of error by sending the correct error message
     * @param client the client to send the error to
     * @param errorCode the error code of the error
     */
    public void error(CustomSocket client, String errorCode){
        String[] param = {errorCode};
        NetworkMessage msg = new NetworkMessage(ProtocolAction.Error, param);
        client.send(msg);
    }

    /**
     * Function which manage a network error , when a player is disconnected
     * @param client the client that is still connected
     */
    public void networkError(CustomSocket client, String existSavePath){
        String[] param = {existSavePath};
        client.send(new NetworkMessage(ProtocolAction.OpponentDisconnected, param));
    }

    /**
     * Function which manage the quit action; the last player is then disconnected and the server is stopped
     * @param client the last connected client
     */
    public void quit(CustomSocket client){
        client.send(new NetworkMessage(ProtocolAction.Quit));
        client.disconnect();
    }

    /**
     * Function which manage the save action.
     * save action is link with the serialization of the grid and the last player who was connected.
     * The serialization create 2 folders, which are stored in a folder name "TicTacToe" directly on the computer
     * of the host in the folder "APPDATA" if the OS=Windows (WIN) or in the folder named "HOME" if OS=Linux
     * @param savename the name of the save
     */
    public void save(String savename){
        try {
            String path;
            if(savePath != null){
                path = savePath;
            }
            else{
                path = getSavePath();
                Files.createDirectories(Paths.get(path));
                path += "/" + savename;
                Files.createDirectories(Paths.get(path));
            }

            //Serialize the grid into json string
            Gson gson = new Gson();
            String json = gson.toJson(grid);
            //Write the json string into a file
            FileWriter writer = new FileWriter(path + "/grid.json");
            writer.write(json);
            writer.close();

            //Serialize the gameinfo into json string
            ArrayList<String> gameInfo = new ArrayList<>();
            gameInfo.add(lastPlayer);
            gameInfo.add("" + isClient1Turn);
            gameInfo.add(grid.getClass().getName());
            json = gson.toJson(gameInfo);
            //Write the json string into a file
            writer = new FileWriter(path + "/gameinfo.json");
            writer.write(json);
            writer.close();

            System.out.println(Text.saved(true));
        } catch (IOException e) {
            System.out.println(Text.saved(false));
            e.printStackTrace();
        }
    }

    /**
     * Function which manage the resume of the game
     * @return if a network error occur while loading game
     */
    public boolean resumeGame(){
        String[] directoryList = null;
        File file = new File(getSavePath());
        if (file.isDirectory()){
            File[] files = file.listFiles();
            if (files != null){
                ArrayList<String> list = new ArrayList<>();
                for (File value : files) {
                    if (value.isDirectory()) {
                        list.add(value.getName());
                    }
                }
                directoryList = new String[list.size()];
                directoryList = list.toArray(directoryList);
            }
        }

        if(directoryList != null){
            client1.send(ProtocolAction.ResumeGame, directoryList);
            NetworkMessage answer;
            try{
                answer = client1.read();
            }
            //Problem with reading the answer
            catch (Exception e){
                //Set answer to null
                answer = new NetworkMessage(ProtocolAction.NONE);
                System.out.println("Error on reading");
            }
            ProtocolAction action = answer.getProtocolAction();

            //If the answer is a message with the action ResumeGame
            if (action == ProtocolAction.ResumeGame) {
                String[] parameters = answer.getParameters();
                try{
                    //If the answer is 0, the server start a new game
                    if(Integer.parseInt(parameters[0]) == 0){
                        return selectDimensions();
                    }
                    //If the answer is the position of a directory is directoryList
                    else if(Integer.parseInt(parameters[0]) <= directoryList.length){
                        String directorySave = directoryList[Integer.parseInt(parameters[0]) - 1];
                        loadGame(directorySave);
                    }
                }
                catch (NumberFormatException ignored){}
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            if(action == ProtocolAction.NetworkError) {
                System.out.println(Text.error("n"));
                return true;
            }
        }
        else return selectDimensions();
        return false;
    }

    private void loadGame(String directorySave) throws IOException {
        //Read file path+"/"+directorySave+"gameinfo.json" into string
        String serializedGameInfo = readSaveFile(directorySave,"gameinfo.json");
        serializedGrid = readSaveFile(directorySave,"grid.json");

        //Deserialize json string into ArrayList<String> gameInfo
        Gson gson = new Gson();
        String json = serializedGameInfo;
        ArrayList<String> gameInfo = gson.fromJson(json, ArrayList.class);
        lastPlayer = gameInfo.get(0);
        isClient1Turn = Boolean.parseBoolean(gameInfo.get(1));
        String className = gameInfo.get(2);

        //Read file path+"/"+directorySave+"grid.json" into string
        serializedGrid = readSaveFile(directorySave,"grid.json");

        //Deserialize json string into Grid grid
        json = serializedGrid;
        if(className.equals("tictactoe.grid.Grid2D")){
            grid = gson.fromJson(json, Grid2D.class);
        }
        else{
            grid = gson.fromJson(json, Grid3D.class);
        }
    }

    /**
     * @param directorySave
     * @param fileName
     * @return serialized save file
     * @throws IOException
     */
    private String readSaveFile(String directorySave,String fileName) throws IOException {
        File jsonGameInfo = new File(getSavePath() + "/" + directorySave + "/" +fileName);
        FileInputStream fileIn = new FileInputStream(jsonGameInfo);
        InputStreamReader isReader = new InputStreamReader(fileIn);
        BufferedReader reader = new BufferedReader(isReader);
        StringBuffer sb = new StringBuffer();
        String str;
        while((str = reader.readLine())!= null){
            sb.append(str);
        }
        reader.close();
        isReader.close();
        fileIn.close();
        return sb.toString();
    }

    /**
     * @return save location depending on the operating system
     */
    private String getSavePath(){
        if(System.getProperty("os.name").toUpperCase().contains("WIN")){
            return System.getenv("APPDATA") + "/TicTacToe";
        }
        return System.getenv(("HOME")) + "/.tictactoe";
    }

    /**
     * @return the client1's socket
     */
    public CustomSocket getClient1(){
        return this.client1;
    }

    /**
     * @return the client2's socket
     */
    public CustomSocket getClient2(){
        return this.client2;
    }

    /**
     * @return the grid
     */
    public Grid getGrid() {
        return grid;
    }
}
