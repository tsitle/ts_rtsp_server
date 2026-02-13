package org.tsitle.rtsp.avdata;

import org.tsitle.rtsp.buffers.BufferExt;
import org.tsitle.rtsp.exceptions.ImageReencoderIoException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.ImagingOpException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

/**
 * Re-encodes an image into a fixed JPEG format (with fixed settings - except for the compression quality).
 */
public class ImageReencoder {

	/**
	 * Use ImageWriter object instead of the 'ImageIO.write()' method.<br />
	 * Only the ImageWriter class supports setting the compression quality.
	 */
	private static final boolean USE_IMAGEWRITER_CLASS = true;

	private float compressionQuality;
	private final ImageWriter writerObj;
	private final JPEGImageWriteParam writerParamObj;

	/**
	 * Constructor.
	 */
	public ImageReencoder() {
		this(1.0f);
	}

	/**
	 * Constructor.<br />
	 * A compression quality setting of 0.0 is interpreted as "high compression is important",
	 * while a setting of 1.0 is interpreted as "high image quality is important".
	 * @param cq Compression quality (range 0.0 .. 1.0)
	 */
	public ImageReencoder(float cq) {
		if (USE_IMAGEWRITER_CLASS) {
			Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
			this.writerObj = writers.next();

			this.writerParamObj = new JPEGImageWriteParam(Locale.getDefault());
			this.writerParamObj.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			this.writerParamObj.setProgressiveMode(ImageWriteParam.MODE_DISABLED);
			this.writerParamObj.setOptimizeHuffmanTables(false);
		} else {
			this.writerObj = null;
			this.writerParamObj = null;
		}
		setCompressionQuality(cq);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Re-encode the image (without hardware acceleration).
	 * @param inputBuf Input image bytes
	 * @param outputBuf Re-encoded image bytes
	 * @throws ImageReencoderIoException If an I/O error occurs
	 */
	public void reencodeImage(BufferExt inputBuf, BufferExt outputBuf) throws ImageReencoderIoException {
		final BufferedImage inputImageOrg = convertBytesIntoImage(inputBuf);

		writeImageToBufferExt(inputImageOrg, outputBuf);
	}

	/**
	 * Scale (and re-encode) the image (without hardware acceleration).
	 * @param inputBuf Input image bytes
	 * @param maxImageWidthHeight Maximum width and height of the image
	 * @param outputBuf Scaled image bytes
	 * @throws ImageReencoderIoException If an I/O error occurs
	 */
	public void scaleImage(BufferExt inputBuf, int maxImageWidthHeight, BufferExt outputBuf) throws ImageReencoderIoException {
		final BufferedImage inputImageOrg = convertBytesIntoImage(inputBuf);

		final BufferedImage bufferedImageRe = internalScaleImage(inputImageOrg, maxImageWidthHeight);

		writeImageToBufferExt(bufferedImageRe, outputBuf);
	}

	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Set the compression quality.<br />
	 * A compression quality setting of 0.0 is interpreted as "high compression is important",
	 * while a setting of 1.0 is interpreted as "high image quality is important".
	 * @param cq Compression quality (range 0.0 .. 1.0)
	 */
	public void setCompressionQuality(float cq) {
		if (cq < 0.01f) {
			cq = 0.0f;
		} else if (cq > 0.99f) {
			cq = 1.0f;
		}
		compressionQuality = cq;
		if (USE_IMAGEWRITER_CLASS) {
			writerParamObj.setCompressionQuality(compressionQuality);
		}
	}

	/**
	 * Get the compression quality.<br />
	 * A compression quality setting of 0.0 is interpreted as "high compression is important",
	 * while a setting of 1.0 is interpreted as "high image quality is important".
	 * @return Compression quality (range 0.0 .. 1.0)
	 */
	@SuppressWarnings("unused")
	public float getCompressionQuality() {
		return compressionQuality;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	/**
	 * Scale the image and return the BufferedImage object.
	 */
	private BufferedImage internalScaleImage(final BufferedImage inputImage, int maxImageWidthHeight)
			throws ImageReencoderIoException {
		int curWidth = inputImage.getWidth(null);
		int curHeight = inputImage.getHeight(null);
		if (curWidth < 1 || curHeight < 1) {
			throw new IllegalArgumentException("Invalid image size");
		}

		int targetW = curWidth;
		int targetH = curHeight;
		final float orgRatio = (float)curWidth / (float)curHeight;

		/*
		 * We scale the image in divide-by-two steps
		 */
		BufferedImage outputImage = inputImage;
		do {
			if (targetW > maxImageWidthHeight && targetW >= targetH) {
				targetW /= 2;
				if (targetW < maxImageWidthHeight) {
					targetW = maxImageWidthHeight;
				}
				targetH = (int)((float)targetW / orgRatio);
			} else if (targetH > maxImageWidthHeight) {
				targetH /= 2;
				if (targetH < maxImageWidthHeight) {
					targetH = maxImageWidthHeight;
				}
				targetW = (int)((float)targetH * orgRatio);
			}
			outputImage = rewriteImageWithScaling(outputImage, targetW, targetH);
		} while (targetW > maxImageWidthHeight || targetH > maxImageWidthHeight);

		return outputImage;
	}

	/**
	 * Re-write the image and return the BufferedImage object.
	 */
	private BufferedImage rewriteImageWithScaling(final BufferedImage inputImage, int targetWidth, int targetHeight)
			throws ImageReencoderIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".rewriteImageWithScaling()";

		try {
			if (inputImage.getType() != BufferedImage.TYPE_3BYTE_BGR) {
				throw new ImageReencoderIoException(FNC_NAME + ": Unsupported image type: " + inputImage.getType());
			}
			BufferedImage scaledImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_3BYTE_BGR);
			float factorX = (float) targetWidth / (float) inputImage.getWidth(null);
			float factorY = (float) targetHeight / (float) inputImage.getHeight(null);
			final AffineTransform at = AffineTransform.getScaleInstance(factorX, factorY);
			final AffineTransformOp ato = new AffineTransformOp(at, AffineTransformOp.TYPE_BICUBIC);
			return ato.filter(inputImage, scaledImage);
		} catch (ImagingOpException e) {
			throw new ImageReencoderIoException(FNC_NAME + ": " + e.getMessage());
		}
	}

	/**
	 * Convert bytes into an image.
	 * @param imageBuf Image bytes
	 * @return Image object
	 * @throws ImageReencoderIoException If an I/O error occurs
	 */
	private BufferedImage convertBytesIntoImage(BufferExt imageBuf) throws ImageReencoderIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".convertBytesIntoImage()";

		try (ByteArrayInputStream bais = new ByteArrayInputStream(imageBuf.getBuf(), 0, imageBuf.getUsed())) {
			return ImageIO.read(bais);
		} catch (IOException e) {
			throw new ImageReencoderIoException(FNC_NAME + ": " + e.getMessage());
		}
	}

	/**
	 * Write an image to a buffer.
	 */
	private void writeImageToBufferExt(BufferedImage outputImage, BufferExt outputBuf) throws ImageReencoderIoException {
		final String FNC_NAME = getClass().getSimpleName() + ".writeImageToBufferExt()";

		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			if (USE_IMAGEWRITER_CLASS) {
				try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
					IIOImage iioImage = new IIOImage(outputImage, null, null);
					writerObj.setOutput(ios);
					writerObj.write(null, iioImage, writerParamObj);
				}
			} else {
				ImageIO.write(outputImage, "jpeg", baos);
			}

			outputBuf.copyOf(baos.toByteArray());
		} catch (IOException e) {
			throw new ImageReencoderIoException(FNC_NAME + ": " + e.getMessage());
		}
	}

}
