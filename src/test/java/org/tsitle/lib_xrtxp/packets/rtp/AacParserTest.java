package org.tsitle.lib_xrtxp.packets.rtp;

import org.junit.jupiter.api.Test;
import org.tsitle.lib_xrtxp.packets.rtp.ParamsContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketAac;
import org.tsitle.lib_xrtxp.avdata.AudioAacInfo;
import org.tsitle.lib_xrtxp.avdata.AudioAacParser;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.helpers.BitWriterHelper;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRtpSeqNr;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoRtpTimestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class AacParserTest {

	@Test
	public void parse_aac() throws Exception {
		BufferExt orgFrame = BufferExt.decodeHexString("""
				fff14c802dbffc211a0fe3fcc9fdfcd5
				d1036182a885612bc3a00ce82da43841
				6d04992a4dbb27d5de6a367078c66e54
				d426b8f94de0c8ca3481ca21878c851f
				a6f1b4ba26c29556bdbd5a85de030abb
				5aa27496519b5b61978c9b59d7be0860
				3364beb60243202bce68c8dc564c7d2a
				d553791ec623f84761682465f240eea0
				84a7cf2eb9d454a1f39c74062ef16bef
				4e7ec6d0638c7fd75c10be7874c25346
				0bdb99d5777e2d4cd70070297128cd8c
				0ba9c83081182e46e1e78528c690c221
				103038100a831194009cb6b8ec50240c
				0d0301610a008221b11474357b715674
				86808e0bc9d8e0c5ea627be7fb402d6d
				aaf2456d0760309f782855f176ce4950
				e5aa34a69e50c30c55e934ae57bfaf15
				09d42532df2aa6bd0f7f9179602cfed6
				23fa96cafad424740652cc9dea37d325
				4329c52dccffa5940e23d20f90c024cd
				16aafb86109c830d13dfd4022057c955
				06ade52ae518a0055b49ca21880004a1
				f8159bdc2146aca883f00f11c0""");

		AudioAacParser aacParser = new AudioAacParser();
		AudioAacInfo aacInfoOrg = aacParser.parseAacData(orgFrame);

		assertEquals(orgFrame.getUsed(), aacInfoOrg.frameLength);
		assertEquals(365, aacInfoOrg.frameLength);
		assertEquals(358, aacInfoOrg.samplesLength);
		assertEquals(7, aacInfoOrg.samplesOffset);
		assertEquals(AudioAacInfo.Samplerate.SR48000, aacInfoOrg.samplerate);
		assertEquals(AudioAacInfo.AudioObjectType.AAC_LC, aacInfoOrg.audioObjectType);
		assertEquals(2, aacInfoOrg.channelConfiguration);

		//
		RtpPacketAac packetOne = new RtpPacketAac(
				new ParamsContainerBase(
						RtspProtoIdXsrc.of(0x12345678L),
						RtspProtoRtpSeqNr.of(0x1234),
						true,
						RtspProtoRtpTimestamp.of(0xABCDEF01L)
					),
				(byte)0,
				aacInfoOrg,
				new BufferView(orgFrame, aacInfoOrg.samplesOffset, aacInfoOrg.samplesLength)
			);

		RtpPacketAac packetTwo = new RtpPacketAac(packetOne.getPacketBufferPtr());

		assertEquals(packetOne.getPacketBufferPtr(), packetTwo.getPacketBufferPtr());

		//
		BufferExt payloadAacTwo = new BufferExt();
		packetTwo.getRawInnerPayloadData(payloadAacTwo);
		//
		BufferExt outputAacFrame = new BufferExt();
		rewriteAdtsHeader(aacInfoOrg, outputAacFrame);
		outputAacFrame.append(payloadAacTwo);

		assertEquals(orgFrame.toHexString(), outputAacFrame.toHexString());

		//
		AudioAacInfo aacInfoTwo = aacParser.parseAacData(outputAacFrame);
		assertEquals(aacInfoOrg.hashSum(), aacInfoTwo.hashSum());

		//
		aacInfoTwo.internalInfo.crc2Bytes[1] = (byte)0x12;
		assertNotEquals(aacInfoOrg.hashSum(), aacInfoTwo.hashSum());
	}

	@SuppressWarnings("DanglingJavadoc")
	private static void rewriteAdtsHeader(AudioAacInfo aacInfo, BufferExt outputAacFrame) {
		BitWriterHelper bitWriter = new BitWriterHelper();

		// --------------------------------------------------------------------
		// Fixed Header - identical for every frame: 28 bits (bytes 0..3.5)
		/// Syncword 0xFFF: bits 0-11 (12 bits)
		bitWriter.writeBits(0xFFF, 12);
		/// ID: bit 12 (1 bit)
		bitWriter.writeBits(aacInfo.internalInfo.idBit ? 1 : 0, 1);
		/// Layer: bits 13-14 (2 bits): Always 00
		bitWriter.writeBits(aacInfo.internalInfo.layer2Bits, 2);
		/// Protection Absent: bit 15 (1 bit): 1 if no CRC, 0 if CRC exists
		bitWriter.writeBits(aacInfo.internalInfo.crcBit ? 0 : 1, 1);
		/// MPEG-4 Audio Object Type: bits 16-17 (2 bits)
		bitWriter.writeBits(aacInfo.audioObjectType.index - 1, 2);
		/// sampling_frequency_index: bits 18-21 (4 bits)
		bitWriter.writeBits(aacInfo.samplerate.index, 4);
		/// Private Bit: bit 22 (1 bit): Set by user
		bitWriter.writeBits(aacInfo.internalInfo.privateBit ? 1 : 0, 1);
		/// channel_configuration: bits 23-25 (3 bits), 1 bit from byte 2 + 2 bits from byte 3
		bitWriter.writeBits(aacInfo.channelConfiguration, 3);
		/// Original/Copy: bit 26 (1 bit)
		bitWriter.writeBits(aacInfo.internalInfo.originalCopyBit ? 1 : 0, 1);
		/// Home: bit 27 (1 bit)
		bitWriter.writeBits(aacInfo.internalInfo.homeBit ? 1 : 0, 1);
		// --------------------------------------------------------------------
		// Variable Header - changes per frame: 28 bits (bytes 3.5..7)
		/// Copyright ID Bit: bit 28 (1 bit)
		bitWriter.writeBits(aacInfo.internalInfo.copyrightIdBit ? 1 : 0, 1);
		/// Copyright ID Start: bit 29 (1 bit)
		bitWriter.writeBits(aacInfo.internalInfo.copyrightIdStartBit ? 1 : 0, 1);
		/// Frame Length: bits 30-42 (13 bits): Length of the frame including header, in bytes
		bitWriter.writeBits(aacInfo.frameLength, 13);
		/// Buffer Fullness: bits 43-53 (11 bits): 0x7FF for VBR (variable bit rate)
		bitWriter.writeBits(aacInfo.internalInfo.bufferFullness11Bits, 11);
		/// Number of RAW Data Blocks: 54-55 (2 bits): Number of AAC frames minus 1
		bitWriter.writeBits(aacInfo.internalInfo.numRawDataBlocks2Bits - 1, 2);
		// --------------------------------------------------------------------
		// CRC - changes per frame: 16 bits (bytes 8..9)
		if (aacInfo.internalInfo.crcBit) {
			bitWriter.writeBits(aacInfo.internalInfo.crc2Bytes[0], 8);
			bitWriter.writeBits(aacInfo.internalInfo.crc2Bytes[1], 8);
		}

		//
		byte[] tmpBa = bitWriter.toByteArray();
		outputAacFrame.copyOf(tmpBa);
	}

}
