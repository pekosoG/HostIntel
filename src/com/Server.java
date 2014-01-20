package com;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Clase encargada del manejo de las conexiones Cliente-Servidor</br>
 * @author Pekosog@IntelCloudTeam
 * @version 1.0
 */
public class Server implements Runnable {
	
	public static final int PUERTO_DEFAULT=5000;
	
	private HashMap<String,Guest> ConexionesGuest=null; //Key = ID
	private Guest ConexionCliente=null;
	private ServerSocket elSocket=null;
	private ExecutorService elEjecutor=null;
	
	/**
	 * Constructor de la Clase Sever</br>
	 * Basicamente crea las instancias de el HashMap y el Ejecutor
	 */
	public Server(){
		this.ConexionesGuest= new HashMap<String,Guest>();
		this.elEjecutor= Executors.newCachedThreadPool();
	}

	/**
	 * Metodo Heredado de Runnable, necesario para generar el Thread</br>
	 * Crea la Instancia del Socket escuchando en el PUERTO_DEFAULT y</br>
	 * tiene un ciclo "infinito" donde escucha las nuevas conexiones y</br>
	 * las envia directamente a el HandShake
	 * @see elHandShake()
	 */
	@Override
	public void run() {
		try{
			elSocket = new ServerSocket(PUERTO_DEFAULT);
		}catch(IOException ee){
			System.out.println("Error al Inicializar el Puerto "+PUERTO_DEFAULT+"\n"+ee);
			return;
		}
		System.out.println("Escuchando en el puerto "+PUERTO_DEFAULT);
		try{
			while(true)
				elHandShake(elSocket.accept());
		}catch(IOException ee){
			System.out.println("Error al Escuchar el Puerto "+PUERTO_DEFAULT+"\n"+ee);
		}catch(Exception e){
			System.out.println("Error desconocido en el Puerto "+PUERTO_DEFAULT+"\n"+e);
		}
	}
	
	/**
	 * Metodo encargado de reconocer si la conexion pertenece</br>
	 * a un programa "Oficial" para la Nube</br></br>
	 * La secuencia es:
	 * <ol>
	 * 		<li>Enviar un ID (que es basicamente un numero aleatorio)</li>
	 * 		<li>Recibir el ID con un Prefijo (el mensaje con prefijo es INTEL-Cloud.[ID] )</li>
	 * 		<li>Enviamos confirmacion ( que es "INTEL-Cloud.OK" )</li>
	 * </ol>
	 * Inmediatamente despues de enviar la confirmacion, se genera una instancia de</br>
	 * Guest y se envia al metodo de getGuestInfo(Guest guest), despues se agrega al catalogo
	 * @see getGuestInfo()
	 * @param socket El socket resultante de la nueva conexion al Servidor
	 */
	private synchronized void elHandShake(Socket socket){
		ObjectOutputStream elOutput=null;
		ObjectInputStream elInput=null;
		
		String tempID=null;
		String tempEntrada="";
		Guest aux=null;
		
		try {
			elOutput= new ObjectOutputStream(socket.getOutputStream());
			elInput = new ObjectInputStream(socket.getInputStream());
		} catch (IOException ioe) {
			System.out.println("Error al Obtener los Streams\n"+ioe);
			return;
		}
		try{
			//Generamos el ID usando el Hash de la IP y sumandole el Time
			tempID=socket.getInetAddress().getHostAddress();
			tempID=String.valueOf(tempID.hashCode()+System.currentTimeMillis());
			
			elOutput.writeObject(tempID);
			
			tempEntrada=(String)elInput.readObject();
			
			if(tempEntrada.equalsIgnoreCase("INTEL-Cloud."+tempID)){
				String ok="INTEL-Cloud.OK";
				elOutput.writeObject(ok);
				
				aux=new Guest(tempID,socket.getInetAddress().getHostAddress(),this);
				aux.setStreams(elInput, elOutput);
				if(getGuestInfo(aux)){
					if(aux.isRoot()){//Esto de Root es para identificar si la conexion es de un usuario o de un Guest
						if(this.ConexionCliente==null)
							this.ConexionCliente=aux;
						else
							return;
					}
					else
						ConexionesGuest.put(aux.getID(),aux);
					elEjecutor.execute(aux.getInput());
				}
			}
		}catch(IOException ioe){
			System.out.println("Problemas con el HandShake\nPeticion de: "+socket.getInetAddress()+"\nException :"+ioe);
			return;
		} catch (ClassNotFoundException e) {
			System.out.println("Problema con el Cast de el Mensaje de entrada en el HandShake\n" +
							   "Peticion de: "+socket.getInetAddress()+"\nException :"+e);
		}
	}
	
	/**
	 * Elimina del catalogo la referencia al guest conectado
	 * @param g Cliente desconectado
	 */
	public void adiosCliente(Guest g){
		String tempID=g.getID();
		
		if(ConexionCliente.getID().equalsIgnoreCase(tempID))
			ConexionCliente=null;
		else
			ConexionesGuest.remove(tempID);
	}
	
	/**
	 * Este metodo se encarga de registrar la informacion basica</br>
	 * de el Guest conectado, que es:
	 * <ul>
	 * 		<li>Sistema Operativo</li>
	 * 		<li>Numero de Cores</li>
	 * 		<li>Memoria Disponible</li>
	 * 		<li>Root</li>
	 * </ul>
	 * Donde basicamente le envia al Cliente las peticiones de</br>
	 * esos datos mediante un mensaje
	 * @param aux Instancia del Cliente al que se le solicitará la informacion
	 * @return TRUE o FALSE dependiendo si todo salio en orden o no
	 */
	private boolean getGuestInfo(Guest aux){
		boolean ok=false;
		String tempMessage=null;
		String tempEntrada=null;
		try{
			tempMessage="GetBasicInfo";
			aux.elOutput.writeObject(tempMessage);
			/*
			 * El Guest deberia de regresarnos un mensaje así:
			 * 	OS=<Sistema_Operativo>;CORES=<Num_Cores>;MEM=<Memoria_Libre>;Root=[1|0]
			 */
			tempEntrada=(String)aux.elInput.readObject();
			String[] splitAux=tempEntrada.split(";");
			
			aux.setOS(splitAux[0].substring(splitAux[0].indexOf('=')));
			aux.setCores(splitAux[1].substring(splitAux[1].indexOf('=')));
			aux.setMem(splitAux[2].substring(splitAux[2].indexOf('=')));
			aux.setRoot(Integer.valueOf(splitAux[3].substring(splitAux[3].indexOf('='))));			
			ok=true;
			
		}catch(IOException ioe){
			System.out.println("Error en el intercambio de datos\n"+ioe);
		} catch (ClassNotFoundException e) {
			System.out.println("Problema con el Cast de el Mensaje de entrada en getGuestInfo()\n" +
					   "Peticion de: "+aux.getIP()+"\nException :"+e);
		}catch(Exception e){
			System.out.println("Problema desconocido en getGuestInfo\n"+e);
		}
		return ok;
	}

	/**
	 * Metodo Encargado de recibir un archivo y regresar la instancia del Archivo</br>
	 * La secuencia sera:
	 * <ul>
	 * 		<li>Nombre del Archivo</li>
	 * 		<li>Archivo</li>
	 * </ul>
	 * @param guest La conexion que lo envia
	 */
	public synchronized File recibeFile(Guest guest){
		File archivo=null;
		String nombre=null;
		try{
			//Rcibimos el nombre del Archivo
			nombre=(String)guest.elInput.readObject();
			//Creamos la Instancia
			archivo = new File(nombre);
			FileOutputStream fos= new FileOutputStream(archivo);
			
			//Recibimos el Archivo desde la conexion
			Object in=guest.elInput.readObject();
            
			//Escribimos el contenido en el archivo
			fos.write((byte[])in);
			
		}catch(EOFException eof){
			System.out.println("Problema EOF al Recibir un archivo desde "+guest.getIP()+"\n"+eof);
			archivo=null;
		}catch(IOException ioe){
			System.out.println("Problema de I/O al Recibir un archivo desde "+guest.getIP()+"\n"+ioe);
			archivo=null;
		}catch(ClassNotFoundException e){
			System.out.println("Problema con el Cast al Recibir un archivo desde "+guest.getIP()+"\n"+e);
			archivo=null;
		}catch(Exception e){
			System.out.println("Problema Desconocido al Recibir un archivo desde "+guest.getIP()+"\n"+e);
			archivo=null;
		}
		return archivo;
	}
	
	/**
	 * Metodo encargado de enviar un archivo a determinada conexion</br>
	 * Esa conexion debe existir en el Catalogo para poder ser enviada.
	 * @param deQuien ID de origen
	 * @param aQuien ID de conexion
	 * @param archivo Archivo a enviar
	 * @return TRUE o FALSE dependiendo si en envió o no
	 */
	public synchronized boolean enviaFile(String deQuien,String aQuien,File archivo){
		boolean ok=false;

		Guest aux=ConexionesGuest.get(aQuien);
		if(ConexionCliente.getID().equalsIgnoreCase(aQuien))
			aux=ConexionCliente;

		if(aux!=null && archivo.exists()){
			String header="FILE-"+deQuien;
			String nombreArch=archivo.getName();
			try{
				//Primero enviamos el Header (FILE-[Origen])
				aux.elOutput.writeObject(header);
				//Despues enviamos el Nombre del Archivo
				aux.elOutput.writeObject(nombreArch);

				//leemos los bytes del archivo
				byte[] bFile=new byte[(int)archivo.length()];

				FileInputStream fio=new FileInputStream(archivo);
				fio.read(bFile);
				//Enviamos el archivo
				aux.elOutput.writeObject(bFile);
				ok=true;
			}catch(IOException e){
				System.out.println("Error I/O al Enviar el archivo "+archivo.getName()+" a "+aux.getIP()+"\n"+e);
			}
		}
		return ok;
	}
	
	/**
	 * Metodo encargado de procesar y reenviar la informacion solicitada.</br>
	 * La informacion que se puede solicitar es:
	 * <ol>
	 * 		<li>GUESTS: Que sería una lista de los Guest Conectados al Servidor *ROOT*</li>
	 * 		<li>STATUS*[ID_GUEST]: Seria una resumen del status de cierto Guest *ROOT*</li>
	 * </ol>
	 * @param g Conexion que hace la peticion
	 * @param msje Mensaje a procesar
	 */
	public synchronized void getInfo(Guest g,String msje){
		if(msje.equalsIgnoreCase("GUESTS")){
			if(g.isRoot())
				sendGuestInfo(g);
			//else
			//	g.sendMensaje("INFO.Request Rechazado");
		}
		//El metodo apra STATUS aun no falta que sea aprovado y planeado...
	}
	
	/**
	 * Metodo que se encarga de enviar el listado completo de los Guest conectados</br>
	 * al servidor, el formato del mensaje es el siguiente:</br>
	 * <ul>
	 * 		<li>{ID=[ID],IP=[IP],OS=[OS],CORES=[CORES],MEM=[MEM]};*</li>
	 * </ul>
	 * @param g
	 */
	private synchronized void sendGuestInfo(Guest g){
		Iterator iter = ConexionesGuest.keySet().iterator();
		String listado="";
        while(iter.hasNext()) { 
                String key = (String)iter.next(); 
                Guest aux = (Guest)ConexionesGuest.get(key); 
                listado+=aux.toString();
        }
        if(listado.length()<10)
        	listado="VACIO";
        try {
			g.elOutput.writeObject(listado);
		} catch (IOException e) {
			System.out.println("Error de IO en sendGuestInfo con "+g.getIP()+"\n"+e);
		}
	}
}
