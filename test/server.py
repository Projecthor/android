#!/usr/bin/python2.7
# -*-coding:Utf-8 -*

from bluetooth import *
import random as rd

server_sock = BluetoothSocket(RFCOMM)
server_sock.bind(("", PORT_ANY))
server_sock.listen(1)

advertise_service(server_sock, "Projecthor", service_classes = [SERIAL_PORT_CLASS], profiles = [SERIAL_PORT_PROFILE])

client_sock, client_info = server_sock.accept()
print "Connexion de", client_info

while True :
	data = client_sock.recv(1024)
	print data
	if data == "c" : # Reçois l'ordre de préparer le tir
		client_sock.sendall("r") # Indique que le robot est prêt
	if data == "f" : # Reçois l'ordre de tirer
		client_sock.sendall("1") # Envoie le score du robot (ici 1)

client_sock.close()
server_sock.close()
