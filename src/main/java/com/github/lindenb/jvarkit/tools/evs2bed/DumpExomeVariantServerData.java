package com.github.lindenb.jvarkit.tools.evs2bed;

import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.soap.SOAPConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import net.sf.picard.cmdline.Option;
import net.sf.picard.cmdline.Usage;
import net.sf.picard.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.github.lindenb.jvarkit.tools.misc.ExtendBed;
import com.github.lindenb.jvarkit.util.picard.AbstractCommandLineProgram;


public class DumpExomeVariantServerData
	extends AbstractCommandLineProgram
	{
	private static Log LOG=Log.getInstance(ExtendBed.class);
    @Usage(programVersion="1.0")
    public String USAGE = getStandardUsagePreamble() + " Download data from EVS. ";

	
    @Option(shortName="N",doc=" (int) download using a step of  'S' bases..",optional=true)
	public int STEP_SIZE=15000;
    
    @Option(shortName="L",doc=" limit to L records (for debugging)",optional=true)
	public long LIMIT=-1L;
   
    
	private long count_records=0L;
	public static final String EVS_NS="http://webservice.evs.gs.washington.edu/";
	private DocumentBuilder documentBuilder;
	private Transformer transformer;
	public DumpExomeVariantServerData()
		{
		}
	
	private static Element first(Element root,String namespaceuri,String localName)
		{
		if(root==null) return null;
		for(Node n=root.getFirstChild();n!=null;n=n.getNextSibling())
			{
			if(n.getNodeType()!=Node.ELEMENT_NODE) continue;
			if(namespaceuri!=null && !namespaceuri.equals(n.getNamespaceURI())) continue;
			if(namespaceuri!=null && !localName.equals(n.getLocalName())) continue;
			if(namespaceuri==null && !localName.equals(n.getNodeName())) continue;
			return Element.class.cast(n);
			}
		return null;
		}
	
	private Element fetchEvsData(String chrom,int start,int end)
		{
		LOG.info(chrom+":"+start+"-"+end+ " N="+count_records);
		try
			{
		    URL url = new URL("http://gvs-1.gs.washington.edu/wsEVS/EVSDataQueryService");
	
		    // Send data
		    URLConnection conn = url.openConnection();
		    conn.setDoOutput(true);
		    PrintStream wr=new PrintStream(conn.getOutputStream());
		    wr.print("<?xml version='1.0' ?>"+
		    		"<S:Envelope xmlns:S='http://schemas.xmlsoap.org/soap/envelope/'>"+
		    		  "<S:Body>"+
		    		    "<ns2:getEvsData xmlns:ns2='http://webservice.evs.gs.washington.edu/'>"+
		    		      "<arg0>"
		    		);
		    wr.print(chrom);
		    wr.print(":");
		    wr.print(String.valueOf(start));
		    wr.print("-");
		    wr.print(String.valueOf(end));
		    wr.print("</arg0>"+
	    		    "</ns2:getEvsData>"+
	    		  "</S:Body>"+
	    		"</S:Envelope>"
	    		);
		    wr.flush();
		    InputStream rd = conn.getInputStream();
		    Document dom=this.documentBuilder.parse(rd);
		    wr.close();
		    rd.close();
		    Element e=first(dom.getDocumentElement(), SOAPConstants.URI_NS_SOAP_ENVELOPE, "Body");
		    e=first(e, EVS_NS, "getEvsDataResponse");
		    e=first(e, null, "return");
		    if(e==null) return null;
			return e;
			}
		catch(Exception err)
			{
			err.printStackTrace();
			return null;
			}
		}
	
	private void fetch(String chrom,int length)
		throws Exception
		{
		if(LIMIT>0 && count_records>=LIMIT) return;
		final int step=this.STEP_SIZE;
		int start=1;
		do
			{
			
			Element root=fetchEvsData(chrom,start,start+step+10);
			for(Node n=(root==null?null:root.getFirstChild());n!=null;n=n.getNextSibling())
				{
				if(n.getNodeType()!=Node.ELEMENT_NODE) continue;
				if(!n.getNodeName().equals("snpList")) continue;
				String chromosome=null;
				String chrPosition=null;
				for(Node n2=n.getFirstChild();n2!=null;n2=n2.getNextSibling())
					{
					if(n2.getNodeType()!=Node.ELEMENT_NODE) continue;
					if(n2.getNodeName().equals("chromosome"))
						{
						chromosome=n2.getTextContent();
						}
					else if(n2.getNodeName().equals("chrPosition"))
						{
						chrPosition=n2.getTextContent();
						}
					}
				count_records++;
				if(LIMIT>0 && count_records>=LIMIT) break;
				
				StringWriter sw=new StringWriter();
				transformer.transform(
						new DOMSource(n),
						new StreamResult(sw)
						);
				sw.flush();
				String xml=sw.toString().replace("\n", "");
				
				System.out.print(chromosome);
				System.out.print('\t');
				System.out.print(Integer.parseInt(chrPosition)-1);
				System.out.print('\t');
				System.out.print(chrPosition);
				System.out.print('\t');
				System.out.println(xml);
				}
			
			
			start+=step;
			if(LIMIT>0 && count_records>=LIMIT) break;
			} while(start<=length);
		}
	
	
	
	@Override
	protected int doWork()
		{
		try {
			DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
			f.setCoalescing(true);
			f.setNamespaceAware(true);
			f.setValidating(false);
			f.setExpandEntityReferences(true);
			f.setIgnoringComments(false);
			this.documentBuilder= f.newDocumentBuilder();
			
			TransformerFactory factory=TransformerFactory.newInstance();
			this.transformer=factory.newTransformer();
			this.transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			
			fetch("1",249250621);
			fetch("2",243199373);
			fetch("3",198022430);
			fetch("4",191154276);
			fetch("5",180915260);
			fetch("6",171115067);
			fetch("7",159138663);		
			fetch("8",146364022);
			fetch("9",141213431);
			fetch("10",135534747);
			fetch("11",135006516);
			fetch("12",133851895);
			fetch("13",115169878);
			fetch("14",107349540);
			fetch("15",102531392);
			fetch("16",90354753);
			fetch("17",81195210);
			fetch("18",78077248);
			fetch("19",59128983);
			fetch("20",63025520);
			fetch("21",48129895);
			fetch("22",51304566);
			fetch("X",155270560);
			//fetch("Y",59373566); not in evs
			//fetch("M",16571);

			
			} 
		catch (Exception e)
			{
			e.printStackTrace();
			return -1;
			}
		return 0;
		}
	
	public static void main(String[] args)
		{
		new DumpExomeVariantServerData().instanceMainWithExit(args);
		}
	}
