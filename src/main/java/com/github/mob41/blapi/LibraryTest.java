package com.github.mob41.blapi;

import com.github.mob41.blapi.mac.Mac; //Necessary if using <code>2.ii</code>

public class LibraryTest {
	public static void main(String[] args){

	System.out.println("Testing Library...");
	try {
		BLDevice dev = new RM2Device("192.168.1.3", new Mac("78:0f:77:17:ec:ee"));
		dev.auth();
		if (dev instanceof RM2Device){
			RM2Device rm2 = (RM2Device) dev;
			
			boolean success = rm2.enterLearning();
			System.out.println("Enter Learning status: " + (success ? "Success!" : "Failed!"));
		} else {
			System.out.println("The \"dev\" is not a RM2Device instance.");
		}
	} catch (Exception e) {
		e.printStackTrace();
	}	
	}
	
}
