package file;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

public class PrintTransientImages 
{
	private static void printProgramUsage(PrintStream out)
	{
		out.println("Usage: PrintTransientImages {fileToPrint|folderToPrint extension} [outName]");
	}
	public static void main(String[] args)
	{
		if(args.length==0){
			System.err.println("Error: Not enough args");
			printProgramUsage(System.out);
			return;
		}
		
		File inf=new File(args[0]);
		
		File outf=null;
		
		if(inf.isDirectory())
		{
			if(args.length<2){
				System.err.println("Error: Not enough args");
				printProgramUsage(System.out);
				return;
			}
			String extension=args[1];
			outf=args.length>2?new File(args[2]):inf;
			outf.mkdir();
			
			for(File f:inf.listFiles())
			{
				if(f.getName().endsWith(extension))
				{
					try {
						HDRDecoder.decodeFloatFile(f, 0.05f, 1,null).printToFile(new File(outf,f.getName().substring(0,f.getName().lastIndexOf("."))+".png"));
					} catch (IOException e) {
						System.err.println("Error printing "+f.getAbsolutePath());
					}
				}
			}
		}
		else if(inf.isFile()){
			outf=args.length>1?new File(args[1]):(new File(inf.getAbsolutePath().substring(0, inf.getAbsolutePath().lastIndexOf("."))+".png"));
			try {
				HDRDecoder.decodeFloatFile(inf, 0.05f, 1,null).printToFile(outf);
			} catch (IOException e) {
				System.err.println("Error printing "+inf.getAbsolutePath());
			}
		}
		else{
			System.err.println("Error: The file "+inf.getAbsolutePath()+" is not a valid file");
		}
	}
}
