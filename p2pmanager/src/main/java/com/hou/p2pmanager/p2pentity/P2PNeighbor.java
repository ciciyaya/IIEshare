package com.hou.p2pmanager.p2pentity;


import java.net.InetAddress;

/**
 * Created by ciciya on 2016/8/1.
 * 局域网的用户
 */
public class P2PNeighbor
{
    public String alias;
    public String ip;
    public InetAddress inetAddress;
    public String imei;
    public int already;


    @Override
    public boolean equals(Object obj)
    {
        if (obj == null)
            return false;

        P2PNeighbor s = (P2PNeighbor) obj;

        if ((s.ip == null))
            return false;

        return (this.ip.equals(s.ip));
    }


}
