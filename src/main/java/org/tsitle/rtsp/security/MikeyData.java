package org.tsitle.rtsp.security;

import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.security.constants.MickeyMsgKemacKv;

class MikeyData {

	public int hdCsbId;
	/**
	 * Number of entries in the {@code hdCsIdMapInfoPolArr}, {@code hdCsIdMapInfoSsrcArr} and {@code hdCsIdMapInfoRocArr} arrays.<br />
	 * Also used to reference Security Policies to those arrays.
	 */
	public byte hdCsNr;
	public byte[] hdCsIdMapInfoPolArr = new byte[0];
	public int[] hdCsIdMapInfoSsrcArr = new int[0];
	public int[] hdCsIdMapInfoRocArr = new int[0];

	public final BufferExt hdRandData = new BufferExt();

	public int spAuthKeyLen = SrtxpKmd.DEFAULT_AUTH_KEY_LEN;

	public final BufferExt kemacMasterKey = new BufferExt();
	public final BufferExt kemacMasterSalt = new BufferExt();
	public final BufferExt kemacTekTgkSalt = new BufferExt();
	public MickeyMsgKemacKv kemacKvType = MickeyMsgKemacKv.MMKEMKV_UNKNOWN;
	public final BufferExt kemacKvDataSpiOrMki = new BufferExt();
	public final BufferExt kemacKvDataIntvF = new BufferExt();
	public final BufferExt kemacKvDataIntvT = new BufferExt();
	public boolean kemacHaveKeys = false;
	public boolean kemacHaveNonTekOnlyPayload = false;

}
