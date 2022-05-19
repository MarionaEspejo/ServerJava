package serverjava;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Servidor {

    private static List<UsuariToken> llistaUsuaris = null;

    public static void main(String[] args) {
        if (args.length < 1) {
            return;
        }

        int port = Integer.parseInt(args[0]);

        try ( ServerSocket serverSocket = new ServerSocket(port)) {

            System.out.println("Server is listening on port " + port);
            llistaUsuaris = new ArrayList<UsuariToken>();

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected");

                new ServerThread(socket).start();
            }

        } catch (IOException ex) {
            System.out.println("Server exception: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public static class ServerThread extends Thread {

        private Socket socket;

        public ServerThread(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                //llegir
                InputStream input = socket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));

                //escriure
                OutputStream output = socket.getOutputStream();
                PrintWriter writer = new PrintWriter(output, true);

                //la primera vegada haura de ser 1, (login)
                int peticioClient = Integer.parseInt(reader.readLine());
                System.out.println("Hem rebut una petició");

                if (peticioClient == 1) {
                    Gson gson = new Gson();
                    String cadenaJson = reader.readLine();
                    UsuariToken usuari = gson.fromJson(cadenaJson, UsuariToken.class);
                    System.out.println("Hem rebut un json");

                    //mirem si el client el tenim registrat a la bd
                    if (LoginCorrecte()) {
                        //generem token
                        usuari.setToken();
                        //enviem el token
                        writer.println(usuari.getToken());
                    } else {
                        //ha fallat
                        writer.println("-1");
                    }
                } else {
                    String tokenRebut = reader.readLine();
                    System.out.println("Token rebut: " + tokenRebut);
                    if (tokenPermes(tokenRebut)) {
                        peticioClient = Integer.parseInt(reader.readLine());
                        switch (peticioClient) {
                            case 2:
                                //getProjectes
                                System.out.println("Vol un 2");
                                break;
                            case 3:
                                //getTasquesAssignades
                                break;
                            case 4:
                                //GetDetallTasca
                                break;
                            case 5:
                                //getNotificacionsPnedents
                                break;
                            case 6:
                                //LlistaUsuaris
                                break;
                            case 7:
                                //NovaEntrada
                                break;
                            default:
                                //fora
                                break;
                        }
                    } else {
                    }
                }

                /*CODI A DESENVOLUPAR ER APP ANDROID*/
                socket.close();
            } catch (IOException ex) {
                System.out.println("Server exception: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    private static boolean LoginCorrecte() {
        return true;
    }

    private static boolean tokenPermes(String token) {
        return true;
    }

}
