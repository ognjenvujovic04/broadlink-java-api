/*******************************************************************************
 * MIT License
 *
 * Copyright (c) 2016, 2017 Anthony Law
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * Contributors:
 *      - Anthony Law (mob41) - Initial API Implementation
 *      - bwssytems
 *      - Christian Fischer (computerlyrik)
 *******************************************************************************/
package com.github.mob41.blapi.pkt;

import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mob41.blapi.BLDevice;
import com.github.mob41.blapi.ex.BLApiRuntimeException;
import com.github.mob41.blapi.mac.Mac;
import com.github.mob41.blapi.pkt.auth.AES;

/**
 * This constructs a byte array with the format of a command to the Broadlink
 * device
 * 
 * @author Anthony
 *
 */
public class CmdPacket implements Packet {

    private static final Logger log = LoggerFactory.getLogger(CmdPacket.class);

    private final byte[] data;

    /**
     * Constructs a command packet
     * 
     * @param targetMac
     *            Target Broadlink device MAC address
     * @param count
     *            Count of packets sent (provided by BLDevice sendPkt())
     * @param id
     *            This BLDevice ID provided by the Broadlink device. It is
     *            {0,0,0,0} if auth() not ran
     * @param aesInstance
     *            The AES encrypt/decrypt instance
     * @param cmdPayload
     *            The data to be sent
     */
    public CmdPacket(Mac targetMac, int count, byte[] id, AES aesInstance, CmdPayload cmdPayload) {

        byte cmd = cmdPayload.getCommand();
        byte[] payload = cmdPayload.getPayload().getData();
        byte[] headerdata;

        log.debug("Constructor CmdPacket starts");
        log.debug("count=" + count + " cmdPayload.cmd=" + Integer.toHexString(cmd) + " payload.len=" + payload.length);

        count = (count + 1) & 0xffff; // increased by the sendPkt()

        log.debug("New count: " + count + " (added by 1)");
        log.debug("Creating byte array with data");

        headerdata = new byte[BLDevice.DEFAULT_BYTES_SIZE];
        for (int i = 0; i < headerdata.length; i++) {
            headerdata[i] = 0x00;
        }

        headerdata[0x00] = 0x5a;
        headerdata[0x01] = (byte) 0xa5;
        headerdata[0x02] = (byte) 0xaa;
        headerdata[0x03] = 0x55;
        headerdata[0x04] = 0x5a;
        headerdata[0x05] = (byte) 0xa5;
        headerdata[0x06] = (byte) 0xaa;
        headerdata[0x07] = 0x55;

        headerdata[0x24] = 0x2a;
        headerdata[0x25] = 0x27;
        headerdata[0x26] = cmd;

        headerdata[0x28] = (byte) (count & 0xff);
        headerdata[0x29] = (byte) (count >> 8);

        byte[] mac = targetMac.getMac();

        headerdata[0x2a] = mac[0];
        headerdata[0x2b] = mac[1];
        headerdata[0x2c] = mac[2];
        headerdata[0x2d] = mac[3];
        headerdata[0x2e] = mac[4];
        headerdata[0x2f] = mac[5];

        headerdata[0x30] = id[0];
        headerdata[0x31] = id[1];
        headerdata[0x32] = id[2];
        headerdata[0x33] = id[3];

        // pad the payload for AES encryption
        byte[] payloadPad = null;
        if(payload.length > 0) {
          int numpad = 16 - (payload.length % 16);

          payloadPad = new byte[payload.length+numpad];
          for(int i = 0; i < payloadPad.length; i++) {
        	  if(i < payload.length)
        		  payloadPad[i] = payload[i];
        	  else
        		  payloadPad[i] = 0x00;
          }
        }

        log.debug("Running checksum for un-encrypted payload");

        int checksumpayload = 0xbeaf;
        for (int i = 0; i < payloadPad.length; i++) {
            checksumpayload = checksumpayload + Byte.toUnsignedInt(payloadPad[i]);
            checksumpayload = checksumpayload & 0xffff;
        }

        headerdata[0x34] = (byte) (checksumpayload & 0xff);
        headerdata[0x35] = (byte) (checksumpayload >> 8);

        log.debug("Un-encrypted payload checksum: " + Integer.toHexString(checksumpayload));

        try {
            log.debug("Encrypting payload");

            payload = aesInstance.encrypt(payloadPad);
            log.debug("Encrypted payload bytes: {}", Base64.getEncoder().encodeToString(payload));

            log.debug("Encrypted. len=" + payload.length);
        } catch (Exception e) {
            log.error("Cannot encrypt payload! Aborting", e);
            throw new BLApiRuntimeException("Cannot encrypt payload", e);
        }

        data = new byte[BLDevice.DEFAULT_BYTES_SIZE + payload.length];
        
        for (int i = 0; i < headerdata.length; i++) {
            data[i] = headerdata[i];
        }

        for (int i = 0; i < payload.length; i++) {
            data[i + BLDevice.DEFAULT_BYTES_SIZE] = payload[i];
        }

        log.debug("Running whole packet checksum");

        int checksumpkt = 0xbeaf;
        for (int i = 0; i < data.length; i++) {
            checksumpkt = checksumpkt + Byte.toUnsignedInt(data[i]);
            checksumpkt = checksumpkt & 0xffff;
//            log.debug("index: " + i + ", data byte: " + Byte.toUnsignedInt(data[i]) + ", checksum: " + checksumpkt);
        }

        log.debug("Whole packet checksum: " + Integer.toHexString(checksumpkt));

        data[0x20] = (byte) (checksumpkt & 0xff);
        data[0x21] = (byte) (checksumpkt >> 8);

        log.debug("End of CmdPacket constructor");
    }

    @Override
    public byte[] getData() {
        return data;
    }

}
