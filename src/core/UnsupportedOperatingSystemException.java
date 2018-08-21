package core;

public class UnsupportedOperatingSystemException extends Exception
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public UnsupportedOperatingSystemException(String osName)
	{
		super(osName);
	}
}
