package org.tsitle.lib_xrtxp.kmd;

import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKdr;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpKmd;
import org.tsitle.lib_xrtxp.kmd.constants.KeySizes;
import org.tsitle.lib_xrtxp.kmd.types.SrtxpMki;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MikeyTest {

	@Test
	void encodeMsgRoundtrip1() throws Exception {
		final String inputMsgB64 = "AQAFALTv+/IBAAAEjxR/AAAAAAsA7W0MlxQSOBEKEIB1A+mz0UfnXMDKIEGhgZwBAAAAGwABAQEBEAIBAQMB" +
				"FAQBDgcBAQgBAQoBAQsBCgAAACcAIQAeaP9/KzYKBaw4GmfXhK5jKD2ITMQKYOvSyn/gHwZwBAn6QCEA";

		SrtxpKmd kmd = MikeyParser.parseMickeyMsgIntoKmd(inputMsgB64);
		final BufferExt expMasterEncKey = BufferExt.decodeHexString("68FF7F2B360A05AC381A67D784AE6328");
		final BufferExt expMasterSalt = BufferExt.decodeHexString("3D884CC40A60EBD2CA7FE01F0670");
		final int expAuthKeyLength = KeySizes.AUTH_KEY_SIZE_160;
		final SrtxpMki expMasterKeyIdentifier = SrtxpMki.of(167395361L, 4);
		final RtspProtoIdXsrc expSsrcId = RtspProtoIdXsrc.of(0x048F147FL);
		final SrtxpKdr expKdrPackets = SrtxpKdr.ofEmpty();

		assertEquals(expMasterEncKey, kmd.masterKey());
		assertEquals(expMasterSalt, kmd.masterSalt());
		assertEquals(expAuthKeyLength, kmd.authKeyLen());
		assertEquals(expMasterKeyIdentifier, kmd.mki());
		assertEquals(expSsrcId, kmd.ssrcId());
		assertEquals(expKdrPackets, kmd.kdr());

		//
		final String outputMsgB64 = MikeyGenerator.generate(kmd);

		kmd = MikeyParser.parseMickeyMsgIntoKmd(outputMsgB64);

		assertEquals(expMasterEncKey, kmd.masterKey());
		assertEquals(expMasterSalt, kmd.masterSalt());
		assertEquals(expAuthKeyLength, kmd.authKeyLen());
		assertEquals(expMasterKeyIdentifier, kmd.mki());
		assertEquals(expSsrcId, kmd.ssrcId());
		assertEquals(expKdrPackets, kmd.kdr());
	}

	@Test
	void encodeMsgRoundtrip2() throws Exception {
		final RtspProtoIdXsrc expSsrcId = RtspProtoIdXsrc.of(0x8F147FABL);

		SrtxpKmd kmdExp = SrtxpKmd.createForMikeyWithDefaults(SrtxpMki.ofAutoSized(1L), expSsrcId);
		final String outputMsgB64 = MikeyGenerator.generate(kmdExp);
		SrtxpKmd kmdActual = MikeyParser.parseMickeyMsgIntoKmd(outputMsgB64);

		assertEquals(kmdExp, kmdActual);
	}

	@Test
	void encodeMsgRoundtrip3() throws Exception {
		final RtspProtoIdXsrc expSsrcId = RtspProtoIdXsrc.of(0x147FAB12L);

		SrtxpKmd kmdExp = SrtxpKmd.createForMikeyWithCustomKeySizes(
				KeySizes.AES_KEY_SIZE_256,
				KeySizes.AUTH_KEY_SIZE_080,
				5,
				SrtxpMki.of(1L, SrtxpKmd.DEFAULT_MKI_LEN),
				expSsrcId
			);
		final String outputMsgB64 = MikeyGenerator.generate(kmdExp);
		SrtxpKmd kmdActual = MikeyParser.parseMickeyMsgIntoKmd(outputMsgB64);

		assertEquals(kmdExp, kmdActual);
	}

	@Test
	void encodeMsgRoundtrip4_notEqual1() throws Exception {
		final RtspProtoIdXsrc expSsrcId = RtspProtoIdXsrc.of(0x147FAB12L);
		final SrtxpMki expMasterKeyIdentifier = SrtxpMki.of(1801278017383574705L, 8);

		SrtxpKmd kmdPre = SrtxpKmd.createForMikeyWithDefaults(SrtxpMki.ofAutoSized(0L), expSsrcId);

		SrtxpKmd kmdInpA = new SrtxpKmd(
				false,
				-1,
				kmdPre.encrKeyLen(),
				kmdPre.masterKey(),
				kmdPre.masterSalt(),
				kmdPre.authKeyLen(),
				kmdPre.authTagLen(),
				expMasterKeyIdentifier,
				expSsrcId,
				SrtxpKdr.ofAutoSized(456789L)
			);
		final String outputMsgB64a = MikeyGenerator.generate(kmdInpA);
		SrtxpKmd kmdResA = MikeyParser.parseMickeyMsgIntoKmd(outputMsgB64a);

		SrtxpKmd kmdInpB = new SrtxpKmd(
				false,
				-1,
				kmdPre.encrKeyLen(),
				kmdPre.masterKey(),
				kmdPre.masterSalt(),
				kmdPre.authKeyLen(),
				kmdPre.authTagLen(),
				expMasterKeyIdentifier,
				RtspProtoIdXsrc.of(expSsrcId.getId32bit().orElse(0L) + 1L),  // <-- modify SSRC
				SrtxpKdr.ofAutoSized(456789L)
			);
		final String outputMsgB64b = MikeyGenerator.generate(kmdInpB);
		SrtxpKmd kmdResB = MikeyParser.parseMickeyMsgIntoKmd(outputMsgB64b);

		assertNotEquals(kmdResA, kmdResB);
	}

	@Test
	void encodeMsgRoundtrip4_notEqual2() throws Exception {
		final RtspProtoIdXsrc expSsrcId = RtspProtoIdXsrc.of(0x147FAB12L);
		final SrtxpMki expMasterKeyIdentifier = SrtxpMki.of(180127801738357470L, 8);

		SrtxpKmd kmdPre = SrtxpKmd.createForMikeyWithDefaults(SrtxpMki.ofAutoSized(0L), expSsrcId);

		SrtxpKmd kmdInpA = new SrtxpKmd(
				false,
				-1,
				kmdPre.encrKeyLen(),
				kmdPre.masterKey(),
				kmdPre.masterSalt(),
				kmdPre.authKeyLen(),
				kmdPre.authTagLen(),
				expMasterKeyIdentifier,
				expSsrcId,
				SrtxpKdr.ofAutoSized(Integer.MAX_VALUE - 10)
			);
		final String outputMsgB64a = MikeyGenerator.generate(kmdInpA);
		SrtxpKmd kmdResA = MikeyParser.parseMickeyMsgIntoKmd(outputMsgB64a);

		SrtxpKmd kmdInpB = new SrtxpKmd(
				false,
				-1,
				kmdPre.encrKeyLen(),
				kmdPre.masterKey(),
				kmdPre.masterSalt(),
				kmdPre.authKeyLen(),
				kmdPre.authTagLen(),
				expMasterKeyIdentifier,
				expSsrcId,
				SrtxpKdr.ofAutoSized(Integer.MAX_VALUE - 101)  // <-- modify KDR
			);
		final String outputMsgB64b = MikeyGenerator.generate(kmdInpB);
		SrtxpKmd kmdResB = MikeyParser.parseMickeyMsgIntoKmd(outputMsgB64b);

		assertNotEquals(kmdResA, kmdResB);
	}

	@Test
	void encodeMsgRoundtrip5_gstreamer() throws Exception {
		/*
		 * This MIKEY message was generated by a buggy Gstreamer RTSP server version with SRTP enabled.
		 * The Auth Key length is 80 bits, which is invalid.
		 */
		final String inputMsgB64 = "AQAFAC68rL4BAAB/b2zoAAAAAAsA7a7vfnjMA3kKEHhniORRvhoL7LkMRUcHD3wBAAAAFQABAQEBEAIBAQMB" +
				"CgcBAQgBAQoBAQAAACIAIAAeim/vByr0sSuJoITtvYYM4gfTtDGlBr1gFXIqCfsMAA==";

		SrtxpKmd kmd = MikeyParser.parseMickeyMsgIntoKmd(inputMsgB64);

		final BufferExt expMasterEncKey = BufferExt.decodeHexString("8A6FEF072AF4B12B89A084EDBD860CE2");
		final BufferExt expMasterSalt = BufferExt.decodeHexString("07D3B431A506BD6015722A09FB0C");
		final int expAuthKeyLength = KeySizes.AUTH_KEY_SIZE_080;
		final SrtxpMki expMasterKeyIdentifier = SrtxpMki.ofEmpty();
		final RtspProtoIdXsrc expSsrcId = RtspProtoIdXsrc.of(0x7F6F6CE8L);
		final SrtxpKdr expKdrPackets = SrtxpKdr.ofEmpty();

		assertEquals(expMasterEncKey, kmd.masterKey());
		assertEquals(expMasterSalt, kmd.masterSalt());
		assertEquals(expAuthKeyLength, kmd.authKeyLen());
		assertEquals(expMasterKeyIdentifier, kmd.mki());
		assertEquals(expSsrcId, kmd.ssrcId());
		assertEquals(expKdrPackets, kmd.kdr());

		//
		final String outputMsgB64 = MikeyGenerator.generate(kmd);

		kmd = MikeyParser.parseMickeyMsgIntoKmd(outputMsgB64);

		assertEquals(expMasterEncKey, kmd.masterKey());
		assertEquals(expMasterSalt, kmd.masterSalt());
		assertEquals(expAuthKeyLength, kmd.authKeyLen());
		assertEquals(expMasterKeyIdentifier, kmd.mki());
		assertEquals(expSsrcId, kmd.ssrcId());
	}

	@Test
	void encodeMsgRoundtrip6_kdr1() throws Exception {
		final SrtxpKdr expKdr = SrtxpKdr.of(Integer.MAX_VALUE, 4);  // 4-byte value

		SrtxpKmd resObj = SrtxpKmd.createForMikeyWithDefaults(SrtxpMki.ofAutoSized(1001L), RtspProtoIdXsrc.of(0x147FAB12L), expKdr);
		final String outputMsgB64 = MikeyGenerator.generate(resObj);
		SrtxpKmd kmdActual = MikeyParser.parseMickeyMsgIntoKmd(outputMsgB64);

		assertEquals(expKdr, kmdActual.kdr());
	}

	@Test
	void encodeMsgRoundtrip6_kdr2() throws Exception {
		final SrtxpKdr expKdr = SrtxpKdr.of(2147483648L, 8);  // 8-byte value

		SrtxpKmd resObj = SrtxpKmd.createForMikeyWithDefaults(SrtxpMki.ofAutoSized(2002L), RtspProtoIdXsrc.of(0x147FAB12L), expKdr);
		final String outputMsgB64 = MikeyGenerator.generate(resObj);
		SrtxpKmd kmdActual = MikeyParser.parseMickeyMsgIntoKmd(outputMsgB64);

		assertEquals(expKdr, kmdActual.kdr());
	}

	@Test
	void encodeMsgRoundtrip6_kdr3() throws Exception {
		final SrtxpKdr expKdr = SrtxpKdr.of(0xFFFFFFFF_FFFFFFAAL, 8);  // 8-byte value

		SrtxpKmd resObj = SrtxpKmd.createForMikeyWithDefaults(SrtxpMki.ofAutoSized(3003L), RtspProtoIdXsrc.of(0x147FAB12L), expKdr);
		final String outputMsgB64 = MikeyGenerator.generate(resObj);
		SrtxpKmd kmdActual = MikeyParser.parseMickeyMsgIntoKmd(outputMsgB64);

		assertEquals(expKdr, kmdActual.kdr());
	}

	@Test
	void encodeMsgRoundtrip7_mki() throws Exception {
		final SrtxpMki expMki = SrtxpMki.of(0xFFFFFFFF_FFFFFFAAL, 8);  // 8-byte value

		SrtxpKmd resObj = SrtxpKmd.createForMikeyWithDefaults(expMki, RtspProtoIdXsrc.of(0x147FAB12L));
		final String outputMsgB64 = MikeyGenerator.generate(resObj);
		SrtxpKmd kmdActual = MikeyParser.parseMickeyMsgIntoKmd(outputMsgB64);

		assertEquals(expMki, kmdActual.mki());
	}

}
