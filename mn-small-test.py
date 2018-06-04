#!/usr/bin/python

import os
import time

from mininet.net import Mininet
from mininet.node import Controller, RemoteController
from mininet.cli import CLI
from mininet.log import setLogLevel, info

filter_ip = open('filter_ip_small.txt', 'r')
list_connect = filter_ip.readlines()

list_final = []
for i in range(len(list_connect)):
    triple = list_connect[i].split(" ")
    triple[0] = triple[0].strip("(h'")
    triple[0] = triple[0].strip("',")
    triple[0] = int(triple[0]) - 1

    triple[1] = triple[1].strip("'h")
    triple[1] = triple[1].strip("')")
    triple[1] = int(triple[1]) - 1

    triple[2] = triple[2].strip()
    triple[2] = float(triple[2])
    list_final.append(triple)

# print(list_final[5])

def myNet():
    net = Mininet( topo=None, build=False)

    host_list = [net.addHost('h'+str(i+1)) for i in range(10)]
    host_list_1 = ['10.0.0.'+str(i+1) for i in range(255)]
    host_list_1 += ['10.0.1.'+str(i-256) for i in range(256, 309)]

    switch_list =  [net.addSwitch('s'+str(i+1), protocols='OpenFlow13') for i in range(3)]
    
    for i in range(2):
	net.addLink(switch_list[i], switch_list[i+1])

    #s1 = net.addSwitch('s1', protocols='OpenFlow13')
    #s2 = net.addSwitch('s2', protocols='OpenFlow13')
    #s3 = net.addSwitch('s3', protocols='OpenFlow13')

    hostsPerSwitch = 308 / 8

    net.addLink(switch_list[0], host_list[0])
    net.addLink(switch_list[0], host_list[1])
    net.addLink(switch_list[0], host_list[2])
    net.addLink(switch_list[0], host_list[3])
    
    net.addLink(switch_list[1], host_list[4])
    net.addLink(switch_list[1], host_list[5])
    net.addLink(switch_list[1], host_list[6])

    net.addLink(switch_list[2], host_list[7])
    net.addLink(switch_list[2], host_list[8])
    net.addLink(switch_list[2], host_list[9])
    #for i in range(8):
    #    for j in range(i*hostsPerSwitch, (i+1)*hostsPerSwitch):
#	    net.addLink(switch_list[i], host_list[j])
#    
#    for i in range(308/8*8, 308):
#	net.addLink(switch_list[7], host_list[i])

    #for i in range(308):
    #    if i < 102:
    #        net.addLink(s1, host_list[i])
    #    elif i < 205:
    #        net.addLink(s2, host_list[i])
    #    else:
    #        net.addLink(s3, host_list[i])

    #net.addLink(s1,s2)
    #net.addLink(s2,s3)
    #net.addLink(s1,s3)

    # Add Controllers
    c0 = net.addController( 'c0', controller=RemoteController, ip='0.0.0.0', port=6653)
    c1 = net.addController('c1', controller=RemoteController, ip='0.0.0.0', port=6654)
    net.build()

    # Connect each switch to a different controller
    for i in range(3):
	switch_list[i].start([c0, c1])

    #s1.start( [c0] )
    #s2.start( [c0] )
    #s3.start( [c0] )

    #print(host_list[4])
    # net.ping([host_list[0], host_list[1]])
    
    raw_input()
    
    for i in range(len(list_final)):
    #for i in range(30):
	print('handlign packet number' + str(i))
        host_list[list_final[i][0]].cmdPrint('fping -c1 -t500 ' + host_list_1[list_final[i][1]])

        if i != len(list_final) - 1:
	    print('sleep for' + str((list_final[i+1][2] - list_final[i][2])/592*30))
            time.sleep((list_final[i+1][2] - list_final[i][2])/592*30)

    # host_list[0].cmdPrint('ping -c 1 10.0.0.2')
    # s1.cmdPrint('ovs-vsctl show')

    # os.system('sudo mn h1 ping h2 -c 1')
    CLI(net)

    # for i in range(len(list_final)):
    #     net.ping(host_list[int(list_final[i][0][1]) - 1], host_list[int(list_final[i][1][1]) - 1])
    net.stop()
#
if __name__ == '__main__':
    setLogLevel( 'info' )
    myNet()
