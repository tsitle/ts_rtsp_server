package org.tsitle.lib_xrtxp.packets.rtcp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_xrtxp.rtsp.exceptions.RtspProtoNumberRangeException;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdXsrc;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * RTCP SSRC/CSRC Block.<br />
 * See <a href="https://datatracker.ietf.org/doc/html/rfc3550#section-6.5">RFC-3550 Section 6.5</a>
 */
public final class RtcpInnerXsrcBlock implements Cloneable {

	public enum BlockType {
		END(0),
		CNAME(1),
		NAME(2),
		EMAIL(3),
		PHONE(4),
		LOC(5),
		TOOL(6),
		NOTE(7),
		PRIV(8),
		UNKNOWN(0xFF);

		private final int value;
		BlockType(int value) { this.value = value; }
		public byte getValue() { return (byte)value; }
		public static @NonNull BlockType of(byte value) {
			for (BlockType type : BlockType.values()) {
				if (type.getValue() == value) {
					return type;
				}
			}
			return UNKNOWN;
		}
	}

	public static class BlockEntry implements Cloneable {
		private final @NonNull BlockType type;
		private @NonNull String prefixValue;  // only for PRIV
		private @NonNull String value;
		public BlockEntry(@NonNull BlockType type, @NonNull String value) {
			this(type, null, value);
		}
		public BlockEntry(@NonNull BlockType type, @Nullable String prefix, @Nullable String value) {
			this.type = type;
			this.prefixValue = (prefix == null || type != BlockType.PRIV ?
					"" :
					(prefix.length() > 255 ? prefix.substring(0, 255) : prefix)
				);
			this.value = (value == null ? "" : (value.length() > 255 ? value.substring(0, 255) : value));
			if (this.prefixValue.length() + this.value.length() > 255) {
				throw new IllegalArgumentException("Total length of prefix and value must be <= 255");
			}
		}
		public @NonNull BlockType getType() { return type; }
		public @NonNull String getPrefixValue() { return prefixValue; }
		public @NonNull String getValue() { return value; }
		public byte getTotalLength() { return (byte)(prefixValue.length() + value.length()); }

		@Override
		public @NonNull String toString() {
			return getClass().getSimpleName() + " [" +
					"Type: " + type +
					(type == BlockType.PRIV ? ", Prefix: '" + prefixValue + "'" : "") +
					", Value: '" + value + "'" +
					"]";
		}

		@Override
		public @NonNull BlockEntry clone() {
			try {
				BlockEntry clonedBlockEntry = (BlockEntry)super.clone();
				//noinspection StringOperationCanBeSimplified
				clonedBlockEntry.prefixValue = new String(prefixValue);
				//noinspection StringOperationCanBeSimplified
				clonedBlockEntry.value = new String(value);
				return clonedBlockEntry;
			} catch (CloneNotSupportedException e) {
				throw new AssertionError();
			}
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/** Minimum size of the RTCP packet payload */
	public static final int PAYLOAD_MIN_SIZE = 4;

	/** Item number - only informative */
	private final int itemNr;

	/** Synchronization/Contributing source identifier (32 bits) */
	private @NonNull RtspProtoIdXsrc bdXsrcId;
	/** Block entries */
	private @NonNull List<@NonNull BlockEntry> bdBlockEntries = new ArrayList<>();

	/**
	 * Constructor.
	 * @param itemNr Item number
	 * @param xsrcId Synchronization/Contributing source identifier
	 * @param blockEntries Block entries - must contain at least one entry
	 */
	public RtcpInnerXsrcBlock(
				int itemNr,
				@NonNull RtspProtoIdXsrc xsrcId,
				@NonNull List<@NonNull BlockEntry> blockEntries
			) {
		if (blockEntries.isEmpty()) {
			throw new IllegalArgumentException("blockEntries is empty");
		}
		this.itemNr = itemNr;
		this.bdXsrcId = xsrcId.clone();
		for (BlockEntry entry : blockEntries) {
			this.bdBlockEntries.add(entry.clone());
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	public int getItemNr() { return itemNr; }

	public @NonNull RtspProtoIdXsrc getXsrcId() { return bdXsrcId.clone(); }

	public @NonNull List<@NonNull BlockEntry> getBlockEntries() {
		List<BlockEntry> resL = new ArrayList<>();
		for (BlockEntry entry : bdBlockEntries) {
			resL.add(entry.clone());
		}
		return resL;
	}

	public int getPayloadSizeForBlock() {
		int resI = PAYLOAD_MIN_SIZE;
		for (BlockEntry entry : bdBlockEntries) {
			resI += 2;  // type + length
			if (entry.getType() == BlockType.PRIV) {
				++resI;  // prefix length
				resI += entry.getPrefixValue().length();
			}
			resI += entry.getValue().length();
		}
		++resI;  // 0x00 termination byte
		if (resI % 4 != 0) {
			resI += (4 - (resI % 4));  // optional padding
		}
		return resI;
	}

	public void appendToBuffer(@NonNull ByteBuffer bb) {
		int totalBytesWritten = 0;
		bb.putInt(bdXsrcId.getId32bit().orElse(0L).intValue());
		totalBytesWritten += 4;
		for (BlockEntry entry : bdBlockEntries) {
			bb.put(entry.getType().getValue());
			++totalBytesWritten;
			bb.put(entry.getTotalLength());
			++totalBytesWritten;
			if (entry.getType() == BlockType.PRIV) {
				// prefix length
				String tmpPrefix = entry.getPrefixValue();
				bb.put((byte)tmpPrefix.length());
				++totalBytesWritten;
				// prefix value
				bb.put(tmpPrefix.getBytes());
				totalBytesWritten += tmpPrefix.length();
			}
			String tmpValue = entry.getValue();
			bb.put(tmpValue.getBytes());
			totalBytesWritten += tmpValue.length();
		}
		// add 0x00 termination byte and optional padding
		do {
			bb.put((byte)0);
			++totalBytesWritten;
		} while (totalBytesWritten % 4 != 0);
	}

	public static @NonNull RtcpInnerXsrcBlock decodeFromBuffer(int itemNr, @NonNull ByteBuffer bb) {
		int totalBytesRead = 0;

		RtspProtoIdXsrc xsrcId = RtspProtoIdXsrc.ofEmpty();
		try {
			xsrcId.setId32bit(Integer.toUnsignedLong(bb.getInt()));
		} catch (RtspProtoNumberRangeException e) {
			// this will never happen
		}
		totalBytesRead += 4;

		List<BlockEntry> blockEntries = new ArrayList<>();
		while (true) {
			byte tmpEntryType = bb.get();
			++totalBytesRead;
			if (tmpEntryType == 0) {  // 0x00 termination byte
				break;
			}
			byte tmpEntryLen = bb.get();
			++totalBytesRead;
			String tmpPrefixValueStr = "";
			if (tmpEntryType == BlockType.PRIV.getValue()) {
				byte tmpPrefixLen = bb.get();
				++totalBytesRead;
				byte[] tmpPrefixValueBa = new byte[tmpPrefixLen];
				bb.get(tmpPrefixValueBa);
				totalBytesRead += tmpPrefixLen;
				tmpPrefixValueStr = new String(tmpPrefixValueBa);
			}
			byte tmpValueLen = (byte)(tmpEntryLen - tmpPrefixValueStr.length());
			byte[] tmpValueBa = new byte[tmpValueLen];
			bb.get(tmpValueBa);
			totalBytesRead += tmpValueLen;
			String tmpValueStr = new String(tmpValueBa);
			//
			BlockEntry entry = new BlockEntry(BlockType.of(tmpEntryType), tmpPrefixValueStr, tmpValueStr);
			blockEntries.add(entry);
		}
		// read optional padding
		while (totalBytesRead % 4 != 0) {
			bb.get();
			++totalBytesRead;
		}

		return new RtcpInnerXsrcBlock(
				itemNr,
				xsrcId,
				blockEntries
			);
	}

	@Override
	public @NonNull String toString() {
		return getClass().getSimpleName() + " [" +
				"ItemNr: " + itemNr +
				", XSRC: " + bdXsrcId.toHexString(true) +
				", " + bdBlockEntries +
				"]";
	}

	@Override
	public @NonNull RtcpInnerXsrcBlock clone() {
		try {
			RtcpInnerXsrcBlock clone = (RtcpInnerXsrcBlock)super.clone();
			clone.bdXsrcId = bdXsrcId.clone();
			clone.bdBlockEntries = new ArrayList<>();
			for (BlockEntry entry : this.bdBlockEntries) {
				clone.bdBlockEntries.add(entry.clone());
			}
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}

}
