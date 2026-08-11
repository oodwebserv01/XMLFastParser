/*
 * e-TaxInvoice Data's Importer Program\r\nVersion : 20180216 by KritsanaWuttisin
 * Project start : 2018-02-16
 * Project finished : 2018-02-22 
 */

package invimportxml;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 *
 * @author KhowTan81
 */
public class InvImportFolder {
	public static final String VERSION = "20220105-BO";
//   public InvImportXML processor = null;
   public InvImportSuperXML processor = null;
   private FileInputStream fir = null;
//   private InputStreamReader isr = null;
   private InputStream isr = null;
   private TxtFileWriter out = null;
   private TxtFileWriter fout = null;
   private TxtFileWriter fout2 = null;
   private int countERROR = 0;
   
   protected boolean flagWarning = false;
   protected boolean flagError = false;
   
   @SuppressWarnings("deprecation")
public static void main(String[] args) throws Exception { //สำหรับ เรียกใชไฟล์นี้โดยตรงด้วย คอมมานไลน์

	   InvImportFolder me = null;
	   String h=null;
	   if (args.length < 2 || (h=args[0].substring(args[0].length()-1)).equals("?")) {
            if (!"?".equals(h)) {System.out.print("Error :  Argument needed.\n");}
            System.out.print("Example : \n java -cp InvImport2018_BO.jar invimportxml.InvImportFolder BO C:/Datas \n");
            System.out.print("   *arg[0] Is Source of Data.\n   *arg[1] Is a folder containing XML files or ZIP files.\n\n");
            System.out.print("Example : \n java -cp InvImport2018_BO.jar invimportxml.InvImportFolder ETDA C:/Datas C:/Outputs \n");
            System.out.print("   *arg[0] Is Source of Data.\n   *arg[1] Is a folder containing XML files or ZIP files.\n   *arg[2] Is a folder will contain TXT files and Log files.\n\n");
            System.out.print("Example : \n java -cp InvImport2018_BO.jar invimportxml.InvImportFolder BO C:/Datas C:/Outputs C:/Logs/job.log \n");
            System.out.print("   *arg[0] Is Source of Data.\n   *arg[1] Is a folder containing XML files or ZIP files.\n   *arg[2] Is a folder will contain TXT files.\n   *arg[3] Is a Log files.\n\n");
            System.out.print("Example : \n java -cp InvImport2018_BO.jar invimportxml.InvImportFolder ETDA C:/Datas C:/Outputs C:/Logs/job.log entity.txt product.txt \n");
            System.out.print("   *arg[0] Is Source of Data.\n   *arg[1] Is a folder containing XML files or ZIP files.\n   *arg[2] Is a folder will contain TXT files.\n   *arg[3] Is a Log files.\n   *arg[4] Is a Name of File to contain Invoices Data.\n   *arg[5] Is a Name of File to contain Products Data\n\n");
            
            System.out.print("Version : "+VERSION+" by Kritsana Wuttisin\r\n");
	   } else {
    		try {
   			    String source = args[0];
                File f = new File(args[1]);
                // ถ้า Path ไม่ถูกต้อง Return Error
                if (!f.exists()) {
                   System.out.print("Error : Folder Path not found.");
                   System.exit(-2);
                   return ;
                }

        		Date now = new Date();
                String _y = ""+(1900+now.getYear()); //_y=_y.substring(1);
                String _m = ""+(101+now.getMonth()); _m=_m.substring(1);
                String _d = ""+(100+now.getDate()); _d=_d.substring(1);
                String hh = ""+(100+now.getHours()); hh=hh.substring(1);
                String mm = ""+(100+now.getMinutes()); mm=mm.substring(1);
                String ss = ""+(100+now.getSeconds()); ss=ss.substring(1);
                String o = (args.length<3)?args[1]:args[2];
                String receiptPath = o+"/"+((args.length<6)?"entity_desc.txt":args[4]);
                String productPath = o+"/"+((args.length<6)?"product_desc.txt":args[5]);
                //String logfullPath = (args.length<4)?o+"/InvImport_"+ _y +"_"+ _m +""+ _d +"_"+ hh +""+mm+""+ss+".log":args[3]+"/InvImport_"+ _y +"_"+ _m +""+ _d +"_"+ hh +""+mm+""+ss+".log";
                String logfullPath = (args.length<4)?o+"/InvImport_"+ _y +"_"+ _m +""+ _d +"_"+ hh +""+mm+""+ss+".log":args[3];
                
                me = new InvImportFolder();
         	    me.flagWarning = false;
         	    me.flagError = false;
                
//                me.processor = new InvImportXML();
                me.processor = new InvImportSuperXML();

                me.processor.source = source;
                me.processor.TotalReceipt = 0;
                me.processor.TotalProduct = 0;
                me.out = new TxtFileWriter();
                me.out.setPath(logfullPath);
                me.fout = new TxtFileWriter();
                me.fout.setPath(receiptPath+".ERR"); // ไฟล์เก็บข้อมูลใบกำกับ
                me.fout2 = new TxtFileWriter();
                me.fout2.setPath(productPath+".ERR"); // ไฟล์เก็บข้อมูลสินค้า          
                me.out.setMsg("Log File of e-TaxInvoice Data's Importer Program\r\nVersion : "+VERSION+" by Kritsana Wuttisin\r\nTime Stamp : "+ _y +"-"+ _m +"-"+ _d +" "+ hh +":"+mm+":"+ss+"\r\n/*----------------------------------------------------------------------------------------------------------------*/\r\n");

                me.TotalProduct = 0;
                me.TotalReceipt = 0;
                long ms=0,sec=0,min=0,hrs=0,start=0;
                start = (new Date()).getTime();
                

                int count = me.Import(f); // ดำเนินการ แปลงไฟล์ และนับจำนวน

        		now = new Date();
                _y = ""+(1900+now.getYear()); //_y=_y.substring(1);
                _m = ""+(101+now.getMonth()); _m=_m.substring(1);
                _d = ""+(100+now.getDate()); _d=_d.substring(1);
                hh = ""+(100+now.getHours()); hh=hh.substring(1);
                mm = ""+(100+now.getMinutes()); mm=mm.substring(1);
                ss = ""+(100+now.getSeconds()); ss=ss.substring(1);

                ms = now.getTime() - start;
                sec = ms/1000; ms = ms%1000;
                min = sec/60; sec = sec % 60;
                hrs = min/60; min = min % 60;
                
                me.out.setMsg("Time Stamp : "+ _y +"-"+ _m +"-"+ _d +" "+ hh +":"+mm+":"+ss+"\r\nTotal success : "+count+" files.\r\nTotalReceipt success : "+me.TotalReceipt+" rows.\r\nTotalProduct success : "+me.TotalProduct+" rows.\r\nTotal error : "+me.countERROR+" files.\r\nTotal import time : "+hrs+" hours  "+min+" minute  "+sec+" sec  "+ms+" ms.\r\n/*----------------------------------------------------------------------------------------------------------------*/\r\n");
                me.fout.close();me.fout=null;
                me.fout2.close();me.fout2=null;
                
                if (me.countERROR!=0 ){
                	me.out.setMsg("Error : "+me.countERROR+" error Detected.");
                    System.out.print("Error : "+me.countERROR+" error Detected.\r\nTotal files : "+count+" files.\r\nTotal expected receipts : "+ me.expectedCount +" rows.\r\nTotalReceipt success : "+me.TotalReceipt+" rows.\r\nTotalProduct success : "+me.TotalProduct+" rows.\r\nTotal error : "+me.countERROR+" files.\r\n");
                    System.out.print("Total translate time : "+hrs+" hours  "+min+" minute  "+sec+" sec  "+ms+" ms.\r\nVersion : "+VERSION+" by Kritsana Wuttisin");
                } else if ( me.expectedCount!=me.TotalReceipt) {
                	me.out.setMsg("Error : The number of Receipts was not matched with the specified by file name.");
                    System.out.print("Error : The number of Receipts was not matched with the specified by file name..\r\nTotal files : "+count+" files.\r\nTotal expected receipts : "+ me.expectedCount +" rows.\r\nTotalReceipt success : "+me.TotalReceipt+" rows.\r\nTotalProduct success : "+me.TotalProduct+" rows.\r\nTotal error : "+me.countERROR+" files.\r\n");
                    System.out.print("Total translate time : "+hrs+" hours  "+min+" minute  "+sec+" sec  "+ms+" ms.\r\nVersion : "+VERSION+" by Kritsana Wuttisin");
                } else {
                	File f1 = new File(receiptPath+".ERR");
                	File f2 = new File(productPath+".ERR");
                	f1.renameTo(new File(receiptPath));
                	f2.renameTo(new File(productPath));
                	f1 = null;
                	f2 = null;
                    System.out.print("Total success : "+count+" files.\r\nTotalReceipt success : "+me.TotalReceipt+" rows.\r\nTotalProduct success : "+me.TotalProduct+" rows.\r\n");
                    System.out.print("Total import time : "+hrs+" hours  "+min+" minute  "+sec+" sec  "+ms+" ms.\r\nVersion : "+VERSION+" by Kritsana Wuttisin\r\n");
                }

                me.out.close();me.out=null;

    		}catch (Exception e) {
    			System.out.print("xml_stack : "+String.copyValueOf(me.processor.stackXML, 0, me.processor.stackXML_p+1));
                System.out.print("Exception : "+e.toString());
                e.printStackTrace();
            }finally{
         		if (me.isr != null) {
           			me.isr.close();
           			me.isr = null;
           		}
           		if (me.fir != null) { 
           			me.fir.close();
           			me.fir = null;
           		}
           		if (me.out != null) {
           			me.out.close();
           			me.out = null;
           		}
           		if (me.fout != null) {
           			me.fout.close();
           			me.fout = null;
           		}
           		if (me.fout2 != null) {
           			me.fout2.close();
           			me.fout2 = null;
           		}
            }                    
    	}
   }

   private int TotalProduct = 0;
   private int TotalReceipt = 0;
   private byte[] bb = new byte[1024];
   int expectedCount = 0;

   private int Import(File f) throws Exception { // f เป็น xml
      int ret = 0;
      // ถ้า Path เป็น File ให้ดำเนินการ
      if (f.isFile()) {
        System.out.print("Open : "+f.getName()+" : "+f.length()+" byte : ");

        long ms=0,sec=0,min=0,hrs=0,start=0,now=0;
        start = (new Date()).getTime();
        
    	String x = ext(f.getName());
		if ("zip".equalsIgnoreCase(x)) {
    		// กรณี  file.zip
			/*
				public void unzipFileIntoDirectory(File archive, File destinationDir) 
				    throws Exception {
				    final int BUFFER_SIZE = 1024;
				    BufferedOutputStream dest = null;
				    FileInputStream fis = new FileInputStream(archive);
				    ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis));
				    ZipEntry entry;
				    File destFile;
				    while ((entry = zis.getNextEntry()) != null) {
				        destFile = FilesystemUtils.combineFileNames(destinationDir, entry.getName());
				        if (entry.isDirectory()) {
				            destFile.mkdirs();
				            continue;
				        } else {
				            int count;
				            byte data[] = new byte[BUFFER_SIZE];
				            destFile.getParentFile().mkdirs();
				            FileOutputStream fos = new FileOutputStream(destFile);
				            dest = new BufferedOutputStream(fos, BUFFER_SIZE);
				            while ((count = zis.read(data, 0, BUFFER_SIZE)) != -1) {
				                dest.write(data, 0, count);
				            }
				            dest.flush();
				            dest.close();
				            fos.close();
				        }
				    }
				    zis.close();
				    fis.close();
				}	 
			*/			
			String names = nameonly(f.getName());
			String[] part = names.split("_");
			expectedCount += Integer.parseInt(part[part.length-1]);
			ZipFile zipFile = null;
			Enumeration<? extends ZipEntry> entries = null;
			try{
				zipFile = new ZipFile(f);
				entries = zipFile.entries();

    		    processor.TotalProduct = 0;
    		    processor.TotalReceipt = 0;
		        String fz = f.getPath();
		        fz = fz.substring(1+fz.replace("\\","/").lastIndexOf("/"));
    		    while(entries.hasMoreElements()){
    		        ZipEntry entry = entries.nextElement();
    		        String fn = entry.getName();
		        	fn = fn.substring(1+fn.replace("\\","/").lastIndexOf("/"));
    		        x = ext(fn);
    		        InputStream zir=null;
    		        String code=null;
    		        if ("xml".equalsIgnoreCase(x)){
    		        	try{
    		        		zir = zipFile.getInputStream(entry);
    		        		/*
	    		        	code = processor.getEncode(zir);
	    		        	zir.close();
	    		        	zir = zipFile.getInputStream(entry);
	    		        	isr = new InputStreamReader(zir,code);
	    		        	 */
	    		        	isr = zir;	
	    		        	int chk=processor.TotalReceipt;
	    		        	processor.Import(isr,out,fout,fout2,fz+"/"+fn);
	    	         	    flagWarning = (flagWarning || processor.flagWarning);
	    	         	    flagError = (flagError || processor.flagError);
 		        	
	    		        	ret++;
	    		            isr.close();
	    		            isr = null;
	    		            zir.close();
	    		        	if (chk==processor.TotalReceipt){
	    		        		flagError = true;
	    		        		countERROR++;
	    		        		out.setMsg("ERROR : file \""+fz+"/"+fn+"\" was skip!\r\n");
    			        		zir = zipFile.getInputStream(entry);
    			        		File folder = new File(f.getPath().substring(0,f.getPath().lastIndexOf(".")));
    			        		if (!folder.exists()) { folder.mkdir(); }
    			        		if (folder.isDirectory()) {
    			        			FileOutputStream err = new FileOutputStream(new File(folder.getPath()+"/"+fn+".err"),true);
	        		        		zir = zipFile.getInputStream(entry);
    			        			processor.sicb = zir.read(bb);
    			        			while (processor.sicb>0){
    			        				err.write(bb, 0, processor.sicb);
    			        				processor.sicb = zir.read(bb);
    			        			}
	        			        	err.close();
	        			        	err = null;
    			        		}
    			        		zir.close();
	    		        	} else if (processor.flagWarning){
    			        		zir = zipFile.getInputStream(entry);
    			        		File folder = new File(f.getPath().substring(0,f.getPath().lastIndexOf(".")));
    			        		if (!folder.exists()) { folder.mkdir(); }
    			        		if (folder.isDirectory()) {
    			        			FileOutputStream err = new FileOutputStream(new File(folder.getPath()+"/"+fn+".wrn"),true);
	        		        		zir = zipFile.getInputStream(entry);
    			        			processor.sicb = zir.read(bb);
    			        			while (processor.sicb>0){
    			        				err.write(bb, 0, processor.sicb);
    			        				processor.sicb = zir.read(bb);
    			        			}
	        			        	err.close();
	        			        	err = null;
    			        		}
    			        		zir.close();	    		        		
	    		        	}
	    		            zir = null;    	
    			        }catch(Exception e){
    			        	countERROR++;
    			        	out.setMsg("ERROR : execption in class = \""+e.getClass().toString()+"\" \r\nMessage : "+e.getMessage()+"\r\nXML File : "+f.getPath()+"/"+fn+"\r\n");
    			        	e.printStackTrace();
    			        	if (isr!=null){
	    			            isr.close();
    			        	}
    			        	if (zir!=null){
    			        		zir.close();
    			        		zir = zipFile.getInputStream(entry);
    			        		File folder = new File(f.getPath().substring(0,f.getPath().lastIndexOf(".")));
    			        		if (!folder.exists()) { folder.mkdir(); }
    			        		if (folder.isDirectory()) {
    			        			FileOutputStream err = new FileOutputStream(new File(folder.getPath()+"/"+fn+".err"),true);
	        		        		zir = zipFile.getInputStream(entry);
    			        			processor.sicb = zir.read(bb);
    			        			while (processor.sicb>0){
    			        				err.write(bb, 0, processor.sicb);
    			        				processor.sicb = zir.read(bb);
    			        			}
	        			        	err.close();
	        			        	err = null;
    			        		}
    			        		zir.close();
    			        		zir = null;
    			        	}
    			        }
    		        }
    		    } 
    		    out.setMsg("Input file : "+fz+"\r\nTotal Receipt : "+processor.TotalReceipt+" record.\r\nTotal Product : "+processor.TotalProduct+" record.\r\n");
    		    TotalReceipt += processor.TotalReceipt;
    		    TotalProduct += processor.TotalProduct;
    		    if (zipFile!=null) {
					zipFile.close();
					zipFile = null;
				}
				if (f!=null) {
					f.deleteOnExit();
					f=null;
				}
			}catch (Exception e){
				countERROR++;
				out.setMsg("ERROR : execption in class = \""+e.getClass().toString()+"\" \r\nMessage : "+e.getMessage()+"\r\nXML File : "+f.getPath()+"\r\n");
				System.out.print("ERROR : execption in class = \""+e.getClass().toString()+"\" \r\nMessage : "+e.getMessage()+"\r\nXML File : "+f.getPath()+"\r\n");
				e.printStackTrace();
			}finally{
				if (zipFile!=null) {
					zipFile.close();
					zipFile = null;
				}
				/*
				if (f!=null) {
					f.deleteOnExit();
					f=null;
				}
				*/
				if (f!=null) {
					f.renameTo(new File(f.getPath()+".ERR"));
					f=null;
				}
	        	out.flush();
	        	fout.flush();
	        	fout2.flush();
			}
		}else if ("xml".equalsIgnoreCase(x)) {
    		// กรณี  file.xml
	        try{
		        //fir = new FileInputStream(f);
		        //String code = processor.getEncode(fir);
		        //fir.close();
		        fir = new FileInputStream(f);
		        //isr = new InputStreamReader(fir,code);
	        	String fn = f.getName();
	        	fn = fn.replace("\\","/").substring(1+fn.lastIndexOf("/"));
	        	//processor.Import(isr,out,fout,fout2,fn);
    		    processor.TotalReceipt=0;
    		    processor.TotalProduct=0;
	        	processor.Import(fir,out,fout,fout2,fn);
         	    flagWarning = (flagWarning || processor.flagWarning);
         	    flagError = (flagError || processor.flagError);
    		    out.setMsg("Input file : "+fn+"\r\nTotal Receipt : "+processor.TotalReceipt+" record.\r\nTotal Product : "+processor.TotalProduct+" record.\r\n");
    		    TotalReceipt += processor.TotalReceipt;
    		    TotalProduct += processor.TotalProduct;
	        	ret++;
	        	/*
	            isr.close();
	            isr = null;
	            */
	            fir.close();
	            fir = null;
	        	//TxtFileWriter.setBackup(f.getPath());
	            if (processor.TotalReceipt==0) {
	            	flagError = true;
	            	countERROR++;
					out.setMsg("File : "+fn+" was skip!\r\n");
					TxtFileWriter.setError(f.getPath());
					f=null;
	            }else if (f!=null) {
					f.deleteOnExit();
					f=null;
				}
	        }catch(Exception e){
	        	countERROR++;
	        	out.setMsg("ERROR : execption in class = \""+e.getClass().toString()+"\" \r\nMessage : "+e.getMessage()+"\r\nXML File : "+f.getPath()+"\r\n");
	        	System.out.print("ERROR : execption in class = \""+e.getClass().toString()+"\" \r\nMessage : "+e.getMessage()+"\r\nXML File : "+f.getPath()+"\r\n");
	        	e.printStackTrace();
	        	out.setMsg(e.getMessage()+"\r\n");
	        	if (isr!=null){
		            isr.close();
		            isr = null;
	        	}
	        	if (fir!=null){
		            fir.close();
		            fir = null;
	        	}
	        	TxtFileWriter.setError(f.getPath());
	        }
		}
		
		now = new Date().getTime();

        ms = now - start;
        sec = ms/1000; ms = ms%1000;
        min = sec/60; sec = sec % 60;
        hrs = min/60; min = min % 60;
        
        out.setMsg("Total import time : "+hrs+" hours  "+min+" minute  "+sec+" sec  "+ms+" ms.\r\n/*----------------------------------------------------------------------------------------------------------------*/\r\n");
		
		System.out.println("\b: done."); 
		ret = 1;
      }else if (f.isDirectory()) { // ถ้า Path เป็น Folder
         FileFilter ff = new FileFilter() {
            public boolean accept(File f) {
               String s = f.getPath();
               String x = ext(s);
               if (f.isDirectory()) return true;
               return ("xml".equalsIgnoreCase(x)||"zip".equalsIgnoreCase(x));
            }
         };
         File[] list = f.listFiles(ff);
         for (int c=0;c<list.length;c++) {
           	ret += Import(list[c]);
         }
      }
      return ret;
   }
   
   private static String ext(String f){
	   String ret = "";
	   int x=-1;
	   if ((x=f.lastIndexOf("."))>=0) {
		   ret = f.substring(1+x);
	   }
	   return ret;
   }

   private static String nameonly(String f){
	   String ret = "";
	   int x=-1;
	   if ((x=f.lastIndexOf("."))>=0) {
		   ret = f.substring(0,x);
	   }
	   return ret;
   }

}
