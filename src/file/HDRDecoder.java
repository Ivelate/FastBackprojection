package file;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferFloat;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Scanner;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.lwjgl.util.vector.Vector3f;

import com.github.ivelate.JavaHDR.HDREncoder;
import com.github.ivelate.JavaHDR.HDRImage;


/**
 * Name change required. Decodes float files and creates transient images from them
 */
public class HDRDecoder 
{
	/*public static HDRImage decodeHdrFile(String route) throws IOException
	{
		URL fileurl=HDRDecoder.class.getClassLoader().getResource(route);
		System.out.println(fileurl.toString());
		DataInputStream in=new DataInputStream(fileurl.openStream());
		int width=
		
		Scanner s=new Scanner(in);
		while(s.hasNextLine())
		{
			System.out.println(s.nextLine());
		}
		System.exit(0);
		return null;
	}*/
	/**
	 * Gets a transient image from a data array float[][][]
	 */
	public static TransientImage loadTransientImageFromMemory(float timeScale,float intensityUnit,Vector3f[] customWallPoints,float[][][] data)
	{
		int width=data.length;
		int height=data[0].length;
		int channels=data[0][0].length;
		float max=0;
		float min=Float.MAX_VALUE;
		//Find max and min
		for(int h=0;h<height;h++)
		{
			for(int w=0;w<width;w++)
			{
				for(int c=0;c<channels;c++)
				{
					if(max<data[w][h][c]) max=data[w][h][c];
					if(data[w][h][c]!=0&&data[w][h][c]<min) min=data[w][h][c];
				}
			}
		}
		
		return loadTransientImageFromMemory(timeScale,intensityUnit,customWallPoints,data,max,min);
	}
	public static TransientImage loadTransientImageFromMemory(float timeScale,float intensityUnit,Vector3f[] customWallPoints,float[][][] data,float max,float min)
	{
		int width=data.length;
		int height=data[0].length;
		int channels=data[0][0].length;
		
		return customWallPoints==null?new TransientImage(width,height,channels,timeScale,intensityUnit,data,max,min):
			new CustomValuesTransientImage(width,height,channels,timeScale,intensityUnit,data,max,min,customWallPoints);
	}
	public static TransientImage decodeFile(File file,float timeScale,float intensityUnit,Vector3f[] customWallPoints) throws IOException
	{
		return decodeFile(file,timeScale,intensityUnit,customWallPoints,null);
	}
	public static TransientImage decodeFile(File file,float timeScale,float intensityUnit,Vector3f[] customWallPoints,float[][][] data) throws IOException
	{
		//Using .float loading by default unless .hdr is explicitly specified
		String fname=file.getName().toLowerCase();
		if(fname.endsWith(".hdr")) return decodeHDRFile(file,timeScale,intensityUnit,customWallPoints,data);
		else return decodeFloatFile(file,timeScale,intensityUnit,customWallPoints,data);
	}
	public static TransientImage decodeFloatFile(File file,float timeScale,float intensityUnit/*,int streak*/,Vector3f[] customWallPoints) throws IOException
	{
		return decodeFloatFile(file,timeScale,intensityUnit/*,streak*/,customWallPoints,null);
	}
	public static TransientImage decodeFloatFile(File file,float timeScale,float intensityUnit/*,int streak*/,Vector3f[] customWallPoints,float[][][] data) throws IOException
	{
		LittleEndianDataInputStream in=new LittleEndianDataInputStream(new BufferedInputStream(new FileInputStream(file)));
		/*byte[] buf=new byte[4];
		in.read(buf);
		byte[] rbuf=new byte[4];
		rbuf[0]=buf[3]; rbuf[1]=buf[2]; rbuf[2]=buf[1]; rbuf[3]=buf[0]; 
		ByteBuffer bufff=ByteBuffer.wrap(rbuf);
		System.out.println(bufff.getInt());*/
		
		int width=in.readInt(); //System.out.println(width);
		int height=in.readInt(); //System.out.println(height);
		int channels=in.readInt(); //System.out.println(channels);
		if(data==null) data=new float[width][height][channels];
		//CustomFloatImage img=data==null?new CustomFloatImage(width,height,channels,timeScale,intensityUnit):new CustomFloatImage(width,height,channels,timeScale,intensityUnit,data);
		//img.setLaserHitTime(0); //wtf
		//img.ystr=streak;
		//img.loadFromFile(in);
		TransientImage img=loadTransientImageFromFloatFile(in,width,height,channels,timeScale,intensityUnit,customWallPoints,data);
		in.close();

		return img;
	}
	public static TransientImage decodeHDRFile(File file,float timeScale,float intensityUnit/*,int streak*/,Vector3f[] customWallPoints,float[][][] data) throws IOException
	{
		//LOAD HDR IMAGE INTO A FLOAT ARRAY
		// Create input stream
		/*ImageInputStream input = ImageIO.createImageInputStream(file);
		BufferedImage image=null;
		try {
		    // Get the reader
		    Iterator<ImageReader> readers = ImageIO.getImageReaders(input);

		    if (!readers.hasNext()) {
		        throw new IllegalArgumentException("No reader for: " + file);
		    }

		    ImageReader reader = readers.next();

		    try {
		        reader.setInput(input);

		        // Disable default tone mapping
		        HDRImageReadParam param = (HDRImageReadParam) reader.getDefaultReadParam();
		        param.setToneMapper(new NullToneMapper());

		        // Read the image, using settings from param
		        image = reader.read(0, param);
		    }
		    finally {
		        // Dispose reader in finally block to avoid memory leaks
		        reader.dispose();
		    }
		}
		finally {
		    // Close stream in finally block to avoid resource leaks
		    input.close();
		}

		// Get float data
		float[] rgb = ((DataBufferFloat) image.getRaster().getDataBuffer()).getData();*/
		HDRImage hdrImg=HDREncoder.readHDR(file, true);
		float[] rgb=hdrImg.getInternalData();
		
		//LOAD TRANSIENT IMAGE FROM FLOAT ARRAY
		int width=hdrImg.getWidth(); //System.out.println(width);
		int height=hdrImg.getHeight(); //System.out.println(height);
		int channels=3; //System.out.println(channels);
		if(data==null) data=new float[width][height][channels];

		float max=0;
		float min=Float.MAX_VALUE;
		for(int h=0;h<height;h++)
		{
			for(int w=0;w<width;w++)
			{
				for(int c=0;c<channels;c++)
				{
					float d=rgb[(h*width + w)*3+c];
					data[w][h][c]=d;
					if(max<d) max=d;
					if(d!=0&&d<min) min=d;
				}
			}
		}
		
		return customWallPoints==null?new TransientImage(width,height,channels,timeScale,intensityUnit,data,max,min):
			new CustomValuesTransientImage(width,height,channels,timeScale,intensityUnit,data,max,min,customWallPoints);
	}
	private static TransientImage loadTransientImageFromFloatFile(LittleEndianDataInputStream in,int width,int height,int channels,float timePerCoord,float intensityMultiplierUnit,Vector3f[] customWallPoints,float[][][] data) throws IOException 
	{
		float max=0;
		float min=Float.MAX_VALUE;
		//float ycos=Vector3f.dot((new Vector3f(0,(this.ystr-64)/64f,1)).normalise(null), new Vector3f(0,0,1));
		//System.out.println(ycos);
		//System.out.println(ycos+" "+this.ystr+" STREK"); 
		for(int h=0;h<height;h++)
		{
			//boolean first=false;
			for(int w=0;w<width;w++)
			{
				float intensityUnits=w*timePerCoord / intensityMultiplierUnit;
				for(int c=0;c<channels;c++)
				{
					float d=in.readFloat();
					/*if(d>0&&!first) first=true;
					else d=0;*/
					//INVERSE SQUARES LAW
					//System.out.println(intensityUnits+" "+w+" "+h);
					//if(this.intensityMultiplierUnit>0) d=d*(intensityUnits*intensityUnits)*ycos; //Used to BOOST intensity
					data[w][h][c]=d;
					if(max<d) max=d;
					if(d!=0&&d<min) min=d;
				}
			}
		}
		
		return customWallPoints==null?new TransientImage(width,height,channels,timePerCoord,intensityMultiplierUnit,data,max,min):
			new CustomValuesTransientImage(width,height,channels,timePerCoord,intensityMultiplierUnit,data,max,min,customWallPoints);
	}
	/*private static final byte[] floatToRgbe(float[] channels)
	{
		float max=channels[0];
		for(int c=1;c<channels.length;c++)
		{
			if(max<channels[c]) max=channels[c];
		}
		if(max<=1e-32)
		{
			for(int c=0;c<channels.length;c++) channels[c]=0;
		}
	}*/
	public static void main(String[] args) throws IOException
	{
		/*FileInputStream fs=new FileInputStream(new File("img/img1.hdr"));
		DataInputStream ds=new DataInputStream(fs);
		Scanner s=new Scanner(fs);
		
		String line="";

		do
		{
			line=s.nextLine();
			System.out.println(line);
		}while(!line.isEmpty());
		line=s.nextLine(); System.out.println(line);
		
		while(ds.available()>0)
		{
			System.out.println(ds.readByte()+" ---> "+ds.readByte()+" "+ds.readByte()+" "+ds.readByte()+" "+ds.readByte());
		}
		System.exit(0);*/
		
		
	/*	File f=new File("TRANSIENTTEST.float");
		System.out.println(f.getAbsolutePath());
		TransientImage im=decodeFloatFile(f,0.005f);
		im.setParamsForCamera(new Vector3f(0.2f,0,1), new Vector3f(0.2f,0,0), (float)Math.toRadians(30),new Vector3f(0,0,0),64);
		
		BufferedImage off_Image =
				  new BufferedImage(im.width, im.height,
				                    BufferedImage.TYPE_INT_RGB);
		
		for(int x=0;x<im.width;x++) for(int y=0;y<im.height;y++) {
			TransientInfo inf=im.get(x, y);
			//System.out.println(inf.time/0.005f);
			if(inf.color[0]!=0) off_Image.setRGB((int)(inf.time/0.005f), y, ~0);
		}
	
		ImageIO.write(off_Image, "PNG", new File("tesss.png"));*/
	}
}
