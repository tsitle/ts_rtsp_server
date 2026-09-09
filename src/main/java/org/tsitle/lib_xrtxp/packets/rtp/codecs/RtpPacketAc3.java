package org.tsitle.lib_xrtxp.packets.rtp.codecs;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.avdata.codec_a_ac3.AudioAc3Info;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.exceptions.BitReaderEosException;
import org.tsitle.lib_xrtxp.common.helpers.BitReaderHelper;
import org.tsitle.lib_xrtxp.common.helpers.BitWriterHelper;
import org.tsitle.lib_xrtxp.packets.rtp.ParamsContainerBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketCodecBase;
import org.tsitle.lib_xrtxp.packets.rtp.RtpPacketType;

/**
 * RTP Packet Payload for AC-3.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc4184">RFC-4184</a>
 */
public final class RtpPacketAc3 extends RtpPacketCodecBase {

	/** Frame Type */
	public enum FrameType {
		/** One or more complete frames */
		FT_ONE_OR_MORE_COMPLETE_FRAMES((byte)0),
		/** Initial fragment of the frame, which includes the first 5/8ths of the frame */
		FT_INITIAL_FRAGMENT_5_8((byte)1),
		/** Initial fragment of the frame, which does not include the first 5/8ths of the frame */
		FT_INITIAL_FRAGMENT_NOT_5_8((byte)2),
		/** Fragment of frame other than the initial fragment */
		FT_NOT_INITIAL_FRAGMENT((byte)3),
		UNKNOWN((byte)255);

		public final byte index;
		FrameType(byte index) {
			this.index = index;
		}
		public static @NonNull FrameType of(byte index) {
			for (FrameType tmpFt : FrameType.values()) {
				if (tmpFt.index == index) {
					return tmpFt;
				}
			}
			return UNKNOWN;
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/** Size of the main payload-specific RTP header */
	public static final int INNER_HEADER_SIZE = 2;

	/** Frame type (2 bits) */
	private FrameType hdFrameType;
	/** Number of fragments or complete frames (if hdFrameType==0) (8 bits) */
	private byte hdNumberOfFragments;

	/**
	 * Constructor.
	 * @param paramsBase Base Container parameters
	 * @param numberOfFragments Number of fragments that the entire AC-3 Syncframe has been split up in (8 bits)
	 * @param isLastFragment Is this the last fragment of the frame?
	 * @param ac3Info AC-3 info
	 * @param payloadView Payload data view
	 */
	public RtpPacketAc3(
				@NonNull ParamsContainerBase paramsBase,
				int numberOfFragments,
				boolean isLastFragment,
				@NonNull AudioAc3Info ac3Info,
				@NonNull BufferView payloadView
			) {
		super(RtpPacketType.A_AC3, paramsBase);

		//
		if (ac3Info.samplerate == AudioAc3Info.Samplerate.UNKNOWN ||
				(ac3Info.bitrate == AudioAc3Info.Bitrate.UNKNOWN)) {
			throw new IllegalArgumentException("Cannot process this kind of AC-3");
		}

		//
		updatePacket(paramsBase, numberOfFragments, isLastFragment, payloadView);
	}

	/**
	 * Constructor.
	 * @param packetData RTP packet bitstream including header and payload
	 */
	public RtpPacketAc3(@NonNull BufferExt packetData) {
		super(RtpPacketType.A_AC3, packetData);

		if (packetData.getUsed() <= RTP_CONT_HEADER_SIZE + INNER_HEADER_SIZE + 1) {  // 1^=inner payload length
			throw new IllegalArgumentException("Invalid RTP packet size (too short)");
		}

		// determine the length of the inner header bitstream
		this.payloadSpecHeaderSize = INNER_HEADER_SIZE;

		//
		BitReaderHelper bitReader = new BitReaderHelper(packetData, RTP_CONT_HEADER_SIZE);
		try {
			// MBZ (Must Be Zero): 6 bits
			int tmpMbz = bitReader.readBits(6);
			if (tmpMbz != 0x00) {
				throw new IllegalArgumentException("Invalid RTP packet: MBZ (Must Be Zero) is not zero");
			}
			// FT (Frame Type): 2 bits
			this.hdFrameType = FrameType.of((byte)bitReader.readBits(2));
			// NF (Number of frames/fragments): 8 bits
			this.hdNumberOfFragments = (byte)bitReader.readBits(8);
		} catch (BitReaderEosException e) {
			throw new RuntimeException(e);
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Update the entire packet.
	 * @param paramsBase Base Container parameters
	 * @param numberOfFragments Number of fragments that the entire AC-3 Syncframe has been split up in (8 bits)
	 * @param isLastFragment Is this the last fragment of the frame?
	 * @param payloadView Payload data view
	 */
	public void updatePacket(
				@NonNull ParamsContainerBase paramsBase,
				int numberOfFragments,
				boolean isLastFragment,
				@NonNull BufferView payloadView
			) {
		if (numberOfFragments < 1 || numberOfFragments > 0xFF) {
			throw new IllegalArgumentException("Invalid number of fragments");
		}

		//
		updatePacketHeader(paramsBase);

		// set inner main header fields
		this.hdFrameType = (numberOfFragments == 1 ?
				FrameType.FT_ONE_OR_MORE_COMPLETE_FRAMES :
				(isLastFragment ? FrameType.FT_NOT_INITIAL_FRAGMENT : FrameType.FT_INITIAL_FRAGMENT_NOT_5_8));
		this.hdNumberOfFragments = (byte)numberOfFragments;

		// build the inner header bitstream
		this.payloadSpecHeaderSize = INNER_HEADER_SIZE;
		byte[] tmpRtpXxxHeader = buildRawInnerHeaderFromFields();
		if (this.payloadSpecHeaderSize != tmpRtpXxxHeader.length) {  // sanity check
			throw new IllegalStateException("Invalid payload-specific header size");
		}
		this.packetBuf.append(tmpRtpXxxHeader);

		// copy the inner payload bitstream
		this.packetBuf.copyFrom(
				payloadView.getInternalBaPtr(),
				payloadView.getOffset(),
				this.packetBuf.getUsed(),
				payloadView.getLength()
			);
	}

	// -----------------------------------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	public @NonNull FrameType getHdFrameType() {
		return hdFrameType;
	}

	@SuppressWarnings("unused")
	public int getHdNumberOfFragments() {
		return hdNumberOfFragments;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private byte[] buildRawInnerHeaderFromFields() {
		BitWriterHelper bitWriter = new BitWriterHelper();

		// MBZ (Must Be Zero): 6 bits
		bitWriter.writeBits(0x00, 6);
		// FT (Frame Type): 2 bits
		bitWriter.writeBits(hdFrameType.index & 0x03, 2);
		// NF (Number of frames/fragments): 8 bits
		bitWriter.writeBits(hdNumberOfFragments & 0xFF, 8);

		return bitWriter.toByteArray();
	}

}
