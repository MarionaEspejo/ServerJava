package serverjava;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import org.mf.persistence.GestioProjectesException;
import org.mf.persistence.IGestioProjectes;
import org.mf.persistence.SingletonGP;
import org.milaifontanals.jdbc.GestioProjectesJDBC;
import org.milaifontanals.model.Entrada;
import org.milaifontanals.model.Estat;
import org.milaifontanals.model.Projecte;
import org.milaifontanals.model.Tasca;
import org.milaifontanals.model.Usuari;
import org.milaifontanals.model.UsuariToken;

public class Servidor {

    static HashMap<String, String> taulaTokenAmbID = new HashMap<>();
    private static List<UsuariToken> llistaUsuaris = null;
    static IGestioProjectes cp;
    static String user = null, password = null, token = null;
    static UsuariToken usu = null;
    static UsuariToken usuEnLinea = null;

    public static void main(String[] args) {

        String nomFitxer = "infoCapa.properties";

        crearCapaPersistencia(nomFitxer);

        //int port = Integer.parseInt(args[0]);
        int port = 6868;

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

                //escriure
                OutputStream output = socket.getOutputStream();

                ObjectOutputStream oos = new ObjectOutputStream(output);
                ObjectInputStream ois = new ObjectInputStream(input);

                //la primera vegada haura de ser 1, (login)
                int queVolFer = ois.readInt(); //llegir

                if (queVolFer == 1) {
                    try {
                        usu = (UsuariToken) ois.readObject();
                        user = usu.getLogin();
                        password = usu.getPasswordHash();
                        usu.setToken();
                        token = usu.getToken();
                        taulaTokenAmbID.put(user, token);
                    } catch (ClassNotFoundException ex) {
                        Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
                    }

                    //mirem si el client el tenim registrat a la bd
                    Servidor srv = new Servidor();
                    if (srv.LoginCorrecte()) {
                        //enviem un OK
                        oos.writeObject("OK");
                        System.out.println("Login realitzat de l'usuari: " + user);
                        //enviem el token perque sempre accedeixi amb el mateix
                        oos.writeObject(usu.getToken());
                    } else {
                        //ha fallat
                        oos.writeObject("KO");
                    }
                } else {
                    String token = null;
                    try {
                        token = (String) ois.readObject();
                    } catch (ClassNotFoundException ex) {
                        Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
                    }

                    // if (tokenRegistrat(token)) {
                    System.out.println("Ja el tenim guardat en memoria");

                    switch (queVolFer) {
                        case 2:
                            //getProjectes
                            System.out.println("Ens demana la llista de projectes");
                             {
                                try {
                                    usu.setID(cp.getID(usu.getLogin(), convertirSHA256(usu.getPasswordHash())));
                                    //enviarem per grup, primer el nom del projecte i despres la seva llista de tasques

                                    List<Projecte> projectes = cp.getLlistaProjectes(cp.getUsuari(usu.getId()));

                                    for (int i = 0; i < projectes.size(); i++) {
                                        List<Tasca> tasques = new ArrayList<>();

                                        tasques = cp.getTasquesIDProj(projectes.get(i).getID());
                                        //per cada tasca, afegim les entrades
                                        for (int f = 0; f < tasques.size(); f++) {
                                            List<Entrada> entrades = cp.getEntradaIDTasca(tasques.get(f).getID());
                                            for (int g = 0; g < entrades.size(); g++) {
                                                tasques.get(f).addEntrada(entrades.get(g));
                                            }
                                        }

                                        for (int f = 0; f < tasques.size(); f++) {
                                            projectes.get(i).addTasca(tasques.get(f));
                                            System.out.println(tasques.get(f).getEntrades().toString());
                                        }
                                    }

                                    oos.writeObject(projectes);
                                } catch (GestioProjectesException ex) {
                                    Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }

                            break;

                        case 3:
                            //getTasquesAssignades
                            System.out.println("Ens demana la llista de tasques");
                            int idProj = 0;
                            try {
                                idProj = (int) ois.readObject();
                            } catch (ClassNotFoundException ex) {
                                Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
                            }
                             {
                                try {
                                    List<Tasca> tasques = cp.getTasquesIDProj(idProj);
                                    oos.writeObject(tasques);
                                } catch (GestioProjectesException ex) {
                                    Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }
                            break;
                        case 4:
                            System.out.println("Ens demana la llista de entrades");
                            int idTasca = ois.readInt();
                            System.out.println("ID tasques: " + idTasca);
                            try {
                                List<Entrada> entrades = cp.getEntradaIDTasca(idTasca);
                                System.out.println("entrada: " + entrades.toString());
                                oos.writeObject(entrades);
                            } catch (GestioProjectesException ex) {
                                Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            break;
                        case 5:
                            System.out.println("Ens demana la llista de projecte de un projecte");

                            Object obj = ois.readObject();
                            String nomProj = (String) obj;
                            System.out.println("Nom projecte: " + nomProj);
                            try {
                                List<Projecte> proj = cp.getProjecteFiltreNom(nomProj);
                                System.out.println(proj.toString());
                                oos.writeObject(proj);
                            } catch (GestioProjectesException ex) {
                                Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            break;
                        case 6:
                            System.out.println("Ens demana la llista estats");
                            List<Estat> llistaEstats = cp.getEstats();
                            oos.writeObject(llistaEstats);
                            break;
                        case 7:
                            System.out.println("Ens demana la llista de projecte amb tasques en un estat");
                            Object objj = ois.readObject();
                            String nomEstat = (String) objj;
                            System.out.println("Nom estat: " + nomEstat);
                            try {
                                List<Projecte> projectes = cp.getLlistaProjectesTascaEstat(nomEstat);
                                for (int i = 0; i < projectes.size(); i++) {
                                    List<Tasca> tasques = cp.getTasquesIDProj(projectes.get(i).getID());
                                    for (int f = 0; f < tasques.size(); f++) {
                                        projectes.get(i).addTasca(tasques.get(f));
                                    }
                                    //per cada tasca, afegim les entrades
                                    for (int f = 0; f < tasques.size(); f++) {
                                        List<Entrada> entrades = cp.getEntradaIDTasca(tasques.get(f).getID());
                                        for (int g = 0; g < entrades.size(); g++) {
                                            tasques.get(f).addEntrada(entrades.get(g));
                                        }
                                    }
                                }
                                System.out.println(projectes.toString());
                                oos.writeObject(projectes);
                            } catch (GestioProjectesException ex) {
                                Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            break;
                        case 8:
                            System.out.println("Ens demana la llista d'usuaris");
                            List<Usuari> llistaUsuaris = cp.getLlistaUsuaris();
                            oos.writeObject(llistaUsuaris);
                            break;

                        case 9:
                            //ens arribara una llista tipus: entrada, NomEstat, NomNovAssigancio, idEscriptor
                            System.out.println("Ens demana crear una entrada");
                            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
                            Date date = new Date();
                            System.out.println(formatter.format(date));
                            int idTask = ois.readInt();
                            System.out.println("ID tasques: " + idTask);
                            Object objEntrda = ois.readObject();
                            List<String> llistaEntrada = (List<String>) objEntrda;

                            System.out.println("Escriptor amb id: " + usu.getId());
                            Usuari escriptor = cp.getUsuari(usu.getId());

                            if (llistaEntrada.get(1).equals("") && llistaEntrada.get(2).equals("-1")) {
                                //no te estat y no te nova assignacio
                                Entrada newEntry = new Entrada(cp.getNumeroEntrada(idTask) + 1, date, llistaEntrada.get(0), null, escriptor, null);
                                cp.NovaEntrada(newEntry, idTask);
                            } else if (llistaEntrada.get(2).equals("-1")) {
                                //si te estat pero no te nova assignacio
                                Estat stat = cp.getEstat(llistaEntrada.get(1));
                                Entrada newEntry = new Entrada(cp.getNumeroEntrada(idTask) + 1, date, llistaEntrada.get(0), stat, escriptor, null);
                                cp.NovaEntrada(newEntry, idTask);
                            } else if (llistaEntrada.get(1).equals("")) {
                                //no te estat pero si te nova assignacio
                                Usuari newAssignacio = cp.getUsuari(Integer.parseInt(llistaEntrada.get(2)));
                                Entrada newEntry = new Entrada(cp.getNumeroEntrada(idTask) + 1, date, llistaEntrada.get(0), null, escriptor, newAssignacio);
                                cp.NovaEntrada(newEntry, idTask);
                            } else {
                                //ho te tot
                                Usuari newAssignacio = cp.getUsuari(Integer.parseInt(llistaEntrada.get(2)));
                                Estat stat = cp.getEstat(llistaEntrada.get(1));

                                Entrada newEntry = new Entrada(cp.getNumeroEntrada(idTask) + 1, date, llistaEntrada.get(0), stat, escriptor, newAssignacio);
                                cp.NovaEntrada(newEntry, idTask);
                            }

                            cp.commit();
                            break;
                        case 10:
                            System.out.println("Ens demana la llista de projecte amb un text de tasca");
                            Object objjTask = ois.readObject();
                            String testTask = (String) objjTask;
                            System.out.println("Text de tasca: " + testTask);
                            try {
                                List<Projecte> projectes = cp.getProjecteFiltreTextTasca(testTask);
                                for (int i = 0; i < projectes.size(); i++) {
                                    List<Tasca> tasques = cp.getTasquesIDProj(projectes.get(i).getID());
                                    for (int f = 0; f < tasques.size(); f++) {
                                        projectes.get(i).addTasca(tasques.get(f));
                                    }
                                    //per cada tasca, afegim les entrades
                                    for (int f = 0; f < tasques.size(); f++) {
                                        List<Entrada> entrades = cp.getEntradaIDTasca(tasques.get(f).getID());
                                        for (int g = 0; g < entrades.size(); g++) {
                                            tasques.get(f).addEntrada(entrades.get(g));
                                        }
                                    }
                                }
                                System.out.println(projectes.toString());
                                oos.writeObject(projectes);
                            } catch (GestioProjectesException ex) {
                                Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            break;
                        default:
                            //fora
                            break;
                    }
                    // } else {
                    // }
                }

                /*CODI A DESENVOLUPAR ER APP ANDROID*/
                socket.close();
            } catch (IOException ex) {
                System.out.println("Server exception: " + ex.getMessage());
                ex.printStackTrace();
            } catch (ClassNotFoundException ex) {
                Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
            } catch (GestioProjectesException ex) {
                Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private boolean LoginCorrecte() {
        try {
            usuEnLinea = cp.Login(user, password);
            System.out.println("trobat");
            return true;
        } catch (GestioProjectesException ex) {
            return false;
        }
    }

    public static String convertirSHA256(String password) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }

        byte[] hash = md.digest(password.getBytes());
        StringBuffer sb = new StringBuffer();

        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }

    private static boolean tokenRegistrat(String token) {
        return taulaTokenAmbID.containsValue(token);
    }

    private static void crearCapaPersistencia(String nomFitxer) {

        Properties props = new Properties();
        try {
            props.load(new FileReader(nomFitxer));
        } catch (FileNotFoundException ex) {
            JOptionPane.showMessageDialog(null, "No es troba fitxer de propietats " + nomFitxer,
                    "Error Capa de Persistencia", JOptionPane.ERROR_MESSAGE);
            return;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Error en carregar fitxer de propietats " + nomFitxer,
                    "Error Capa de Persistencia", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String nomCapa = props.getProperty("nomCapa");
        if (nomCapa == null || nomCapa.equals("")) {
            JOptionPane.showMessageDialog(null, "Fitxer de propietats " + nomFitxer + " no conté propietat nomCapa",
                    "Error Capa de Persistencia", JOptionPane.ERROR_MESSAGE);
            return;
        }

        cp = null;

        try {
            cp = SingletonGP.getGestorProjectes(nomCapa);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "Error en crear capa de persistencia",
                    "Error Capa de Persistencia", JOptionPane.ERROR_MESSAGE);
            return;
        }
    }

}
