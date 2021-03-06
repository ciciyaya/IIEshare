package com.hou.p2pmanager.p2pcore;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.hou.p2pmanager.p2putils.P2PConstant;
import com.hou.p2pmanager.p2pentity.P2PNeighbor;
import com.hou.p2pmanager.p2pentity.SigMessage;
import com.hou.p2pmanager.p2pentity.param.ParamIPMsg;
import com.hou.p2pmanager.p2putils.RSAUtil;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;

/**
 * Created by ciciya on 2016/8/11.
 * 接收端和发送端的udp交互
 */
public class UDPCommunicate extends Thread {

    private static final String tag = UDPCommunicate.class.getSimpleName();

    private P2PHandler p2PHandler;
    private P2PManager p2PManager;

    private DatagramSocket udpSocket;
    private DatagramPacket sendPacket;
    private DatagramPacket receivePacket;
    private byte[] resBuffer = new byte[P2PConstant.BUFFER_LENGTH];//创建接收数据包byte数组
    private byte[] sendBuffer = null;//创建发送数据包
    private String[] mLocalIPs;
    private boolean isStopped = false;

    private Context mContext;
    private RSAUtil rsa = new RSAUtil();

    public UDPCommunicate(P2PManager manager, P2PHandler handler, Context context) {
        mContext = context;
        this.p2PHandler = handler;
        this.p2PManager = manager;
        setPriority(MAX_PRIORITY);
        init();
    }

    private void init() {
        mLocalIPs = getLocalAllIP();
        try {
            //udpSocket = new DatagramSocket(P2PConstant.PORT);
            udpSocket = new DatagramSocket(null);
            udpSocket.setReuseAddress(true);
            udpSocket.bind(new InetSocketAddress(P2PConstant.PORT));//将端口号和socket绑定
        }
        catch (SocketException e) {
            e.printStackTrace();
            if (udpSocket != null) {
                udpSocket.close();
                isStopped = true;
                return;
            }
        }
        //待接收数据包
        receivePacket = new DatagramPacket(resBuffer, P2PConstant.BUFFER_LENGTH);
        isStopped = false;
    }

    @Override
    public void run() {
        while (!isStopped) {
            try {
                //开始接收数据包
                udpSocket.receive(receivePacket);
            } catch (IOException e) {
                e.printStackTrace();
                isStopped = true;
                break;
            }
            if (receivePacket.getLength() == 0) {
                continue;
            }

            String strReceive;
            try {
                //获取数据包中内容
                strReceive = new String(resBuffer, 0, receivePacket.getLength(),
                        P2PConstant.FORMAT);
                /*//解密后的字符串
                try {
                    RSAPrivateKey priKey = rsa.getRSAPrivateKey();
                    byte[] deRsaBytes = rsa.decrypt(priKey, strReceive.getBytes());
                    deRsaStr = new String(deRsaBytes, "gbk");
                    //System.out.println("解密后==" + deRsaStr);
                } catch (Exception e) {
                    e.printStackTrace();
                }*/


            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                continue;
            }
            String ip = receivePacket.getAddress().getHostAddress();

            if (!TextUtils.isEmpty(ip)) {
                if (!isLocal(ip)) //自己会收到自己的广播消息，进行过滤
                {
                    if (!isStopped) {
                        Log.d(tag, "sig communicate process received udp message = "
                                + strReceive);
                        ParamIPMsg msg = new ParamIPMsg(
                                strReceive, receivePacket.getAddress(), receivePacket.getPort());

                        p2PHandler.send2Handler(
                                msg.peerMSG.commandNum, P2PConstant.Src.COMMUNICATE,
                                msg.peerMSG.recipient, msg);
                    } else {
                        break;
                    }
                }
            } else {
                isStopped = true;
                break;
            }
            //
            if (receivePacket != null)
                receivePacket.setLength(P2PConstant.BUFFER_LENGTH);
        }

        release();
    }

    //将自己的信息广播出去
    public void BroadcastMSG(int cmd, int recipient) {
        try {
            sendMsg2Peer(P2PManager.getBroadcastAddress(mContext), cmd, recipient, null);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public void sendMsg2Peer(InetAddress sendTo, int cmd, int recipient, String add) {
        SigMessage sigMessage = getSelfMsg(cmd);
        if (add == null)
            sigMessage.addition = "null";
        else
            sigMessage.addition = add;
        sigMessage.recipient = recipient;

        sendUdpData(sigMessage.toProtocolString(), sendTo);
    }

    //Socket UDP发送函数
    private synchronized void sendUdpData(String sendStr, InetAddress sendTo) {
        try {
           /* //rsa加密后字符串
            String enRsaStr = null;
            try {
                RSAPublicKey pubKey = rsa.getRSAPublicKey();
                byte[] enRsaBytes = rsa.encrypt(pubKey, sendStr.getBytes());
                enRsaStr = new String(enRsaBytes, "gbk");
                //System.out.println("加密后==" + enRsaStr);
            } catch (Exception e) {
                e.printStackTrace();
            }*/

            //发送byte数据
            sendBuffer = sendStr.getBytes(P2PConstant.FORMAT);
            //新建数据包
            sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, sendTo,
                    P2PConstant.PORT);
            if (udpSocket != null) {
                //发送数据包
                udpSocket.send(sendPacket);
                Log.d(tag, "send udp data = " + sendStr + "; send to = " + sendTo.getHostAddress());
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void quit() {
        isStopped = true;
        release();
    }

    private void release() {
        Log.d(tag, "sigCommunicate release");
        if (udpSocket != null)
            udpSocket.close();
        if (receivePacket != null)
            receivePacket = null;
    }

    private SigMessage getSelfMsg(int cmd) {
        SigMessage msg = new SigMessage();
        msg.commandNum = cmd;
        P2PNeighbor melonInfo = p2PManager.getSelfMeMelonInfo();
        if (melonInfo != null) {
            msg.senderAlias = melonInfo.alias;
            msg.senderImei = melonInfo.imei;
            msg.senderIp = melonInfo.ip;
        }

        return msg;
    }

    private boolean isLocal(String ip) {
        for (int i = 0; i < mLocalIPs.length; i++) {
            if (ip.equals(mLocalIPs[i]))
                return true;
        }

        return false;
    }

    private String[] getLocalAllIP() {
        ArrayList<String> IPs = new ArrayList<String>();

        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            // 遍历所用的网络接口
            while (en.hasMoreElements()) {
                NetworkInterface nif = en.nextElement();// 得到每一个网络接口绑定的所有ip
                Enumeration<InetAddress> inet = nif.getInetAddresses();
                // 遍历每一个接口绑定的所有ip
                while (inet.hasMoreElements()) {
                    InetAddress ip = inet.nextElement();
                    if (!ip.isLoopbackAddress()
                            && ip instanceof Inet4Address) {
                        IPs.add(ip.getHostAddress());
                    }
                }

            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return IPs.toArray(new String[]{});
    }

}