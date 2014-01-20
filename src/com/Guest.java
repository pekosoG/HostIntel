package com;

import java.io.EOFException;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.SocketException;

/**
 * Clase que se contiene la informacion basica de los GUEST</br>
 * que están conectados a la Nube
 * <ul>
 * 		<li>Numero ID</li>
 * 		<li>Sistema Operativo</li>
 * 		<li>Direccion IP</li>
 * 		<li>Numero Cores</li>
 * 		<li>Memoria Disponible</li>
 * </ul>
 * @author Pekosog@IntelCloudTeam
 * @version 1.0
 */
public class Guest {
	
	private String ID=null;
	private String OS=null;
	private String IP=null;
	private String numCores=null;
	private String memoriaLibre=null;
	private int root=0; //Este es en caso de que sea un Cliente (Betty)
	
	Server server=null;
	ObjectInputStream elInput;
	ObjectOutputStream elOutput;
	
	public Guest(String ID,String IP,Server s){
		this.ID=ID;
		this.IP=IP;
		this.server=s;
	}
	
	public void setRoot(int i){
		if(i==1)
			this.root=i;
	}
	public boolean isRoot(){
		return this.root==1?true:false;
	}
	
	public void setStreams(ObjectInputStream elInput,ObjectOutputStream elOutput){
		this.elInput=elInput;
		this.elOutput=elOutput;
	}
	
	public String getID() {
		return ID;
	}	
	public String getIP() {
		return IP;
	}
	
	public String getOS() {
		return OS;
	}
	public void setOS(String OS){
		this.OS=OS;
	}

	public void setCores(String C){
		this.numCores=C;
	}
	public String getCores(){
		return this.numCores;
	}
	
	public void setMem(String M){
		this.memoriaLibre=M;
	}
	public String getMem(){
		return this.memoriaLibre;
	}
	
	public Runnable getInput(){
		return null;
	}
	
	@Override
	public String toString(){
		//{ID=[ID],IP=[IP],OS=[OS],CORES=[CORES],MEM=[MEM]}
		return "{ID="+this.ID+",IP="+this.IP+",OS="+this.OS+",CORES="+this.numCores+",MEM="+this.memoriaLibre+"};";
}

/**
 * Clase encargada de estar escuchando el InputStream de la Conexion</br>
 * generando un Hilo(Thread) por cada conexion
 * @author Pekosog@IntelCloudTeam
 * @version 1.0
 */
class elInput implements Runnable{
	
	Guest guest=null;
	
	public elInput(Guest g){
		this.guest=g;
	}
	
	/**
	 * Metodo encargado de recibir los mensajes
	 */
	@Override
	public void run() {
		try{
			Object in=guest.elInput.readObject();
			do{
				String msje=(String)in;
				procesaMensaje(msje);
			}while(in!=null);
		}catch(EOFException eof){
			System.out.println("Error EOF con "+guest.getIP()+"\n"+eof);
		}catch(SocketException se){
			System.out.println("Error de Socket con "+guest.getIP()+"\n"+se);
		}catch(NullPointerException npe){
			System.out.println("Error de NullPointer con "+guest.getIP()+"\n"+npe);
		}catch(Exception ex){
			System.out.println("Excepcion desconocida con "+guest.getIP()+"\n"+ex);
		}finally{
			guest.server.adiosCliente(guest);
			System.out.println("Sesion con "+guest.getIP()+" Terminada");
		}
	}
	
	/**
	 * Metodo encargado de procesar los mensajes recibidos de la conexion</br>
	 * Los mensajes podran ser los siguientes:
	 * <ul>
	 * 		<li>File-[Destino] : Este mensaje indica que inmediatamente despues, llegará un archivo y </br>
	 * 			a que Guest corresponde</li>
	 * 		<li>Info-[Mensaje] : Este mensaje indica que está enviando informacion de su estado:</br>
	 * 			<ul>
	 * 				<li>REQ=[peticion]: Que sería peticion de informacion</li>
	 * 			</ul>
	 * 		</li>
	 * 		<li>Error-[Mensaje] : Indica que ocurrió un error con la prueba/sistema</li> 
	 * </ul>
	 * [NOTA] Si creen que haga falta agregar algun mensaje, vemos y lo agregamos 
	 * @param msje Mensaje a procesar
	 */
	private void procesaMensaje(String msje){
		String aux[]=msje.split("-");
		String header=aux[0];
		String body=aux[1];
		
		/*
		 * Este es el unico metodo en el que intervienen los input stream de manera mas "Directa"
		 * Intentaré mejorarlo para que no afecte tanto al Thread original (en caso de que haga mas lento)
		 * */
		if(header.equalsIgnoreCase("File")){
			try{
				File arch=guest.server.recibeFile(guest);//Recibimos el Archivo
				String aQuien=(String)guest.elInput.readObject();
				if(guest.server.enviaFile(guest.getID(), aQuien, arch)){
					System.out.println("Archivo enviado");
					arch.delete();
				}
			}catch(Exception e){
				System.out.println("Error al Procesar el mensaje de FILE con "+guest.getIP()+"\n"+e);
			}
		}
		if(header.equalsIgnoreCase("Info")){
			guest.server.getInfo(guest,body);
		}
		if(header.equalsIgnoreCase("Error")){
		}
			
	}
	
}