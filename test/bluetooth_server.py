#!/usr/bin/python2.7 
# -*-coding:Utf-8 -*

from bluetooth import *
import random as rd

server_sock = BluetoothSocket(RFCOMM)
server_sock.bind(("", PORT_ANY))
server_sock.listen(1)

advertise_service(server_sock, "RoboTXBTController", service_classes = [SERIAL_PORT_CLASS], profiles = [SERIAL_PORT_PROFILE])

client_sock, client_info = server_sock.accept()
print "Connexion de", client_info

data = client_sock.recv(1024)
print "Difficulty : ", data 
client_sock.sendall(data);

cont=True

while cont :
	data = client_sock.recv(1024)
	print data
	if data == "Fire":
		print "Order to fire"
		score = rd.randint(0,100)
		client_sock.sendall( str(score) )
	elif data == "Quit":
		print "Exiting"
	else:
		print "Invalid command"

client_sock.close()
server_sock.close()
