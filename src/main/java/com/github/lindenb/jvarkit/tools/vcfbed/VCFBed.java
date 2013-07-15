package com.github.lindenb.jvarkit.tools.vcfbed;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;


import org.broad.tribble.readers.LineReader;
import org.broad.tribble.readers.TabixReader;
import org.broadinstitute.variant.variantcontext.VariantContext;
import org.broadinstitute.variant.variantcontext.VariantContextBuilder;
import org.broadinstitute.variant.variantcontext.writer.VariantContextWriter;
import org.broadinstitute.variant.vcf.VCFCodec;
import org.broadinstitute.variant.vcf.VCFHeader;

import com.github.lindenb.jvarkit.util.AbstractVCFFilter;





public class VCFBed extends AbstractVCFFilter
	{
	public String FORMAT="${1}:${2}-${3}";
	public String TABIXFILE;
	public String TAG="TAG";
	private Set<String> extraHeaderLine=new LinkedHashSet<String>();
	
	
	private static abstract class Chunk
		{
		public abstract String toString(String tokens[]);
		public Chunk next=null;
		}
	
	private static class PlainChunk extends Chunk
		{
		String s;
		PlainChunk(String s){ this.s=s;}
		public String toString(String tokens[])
			{
			return s+(next==null?"":next.toString(tokens));
			}
		}
	private static class ColChunk extends Chunk
		{
		int index;
		ColChunk(int index){ this.index=index;}
		public String toString(String tokens[])
			{
			return tokens[index]+(next==null?"":next.toString(tokens));
			}
		}

	
	private Chunk parseFormat(String s,int i)
		{
		if(i>=s.length()) return null;
		if(s.startsWith("${"))
			{
			int j=s.indexOf('}',2);
			if(j==-1) throw new IllegalArgumentException("bad format");
			int col=Integer.parseInt(s.substring(i+2, j).trim());
			if(col<1) throw new IllegalArgumentException();
			ColChunk c=new ColChunk(col-1);
			c.next=parseFormat(s, j+1);
			return c;
			}
		else if(s.startsWith("${"))
			{
			int j=i+1;
			while(j<s.length() && Character.isDigit(s.charAt(j)))
				{
				++j;
				}
			int col=Integer.parseInt(s.substring(i+1, j).trim());
			if(col<1) throw new IllegalArgumentException();
			ColChunk c=new ColChunk(col-1);
			c.next=parseFormat(s, j+1);
			return c;
			}
		
		StringBuilder sb=new StringBuilder();
		while(i< s.length() && s.charAt(i)!='$')
			{
			sb.append(s.charAt(i));
			i++;
			}
		PlainChunk c=new PlainChunk(sb.toString());
		c.next=parseFormat(s,i);
		return c;
		}
	
	
	
	@Override
	protected void doWork(LineReader in, VariantContextWriter w)
			throws IOException
		{
		Pattern tab=Pattern.compile("[\t]");
		Chunk parsedFormat=parseFormat("TODO",0);
		TabixReader tabix= new TabixReader(this.TABIXFILE);
		VCFCodec codeIn=new VCFCodec();		
		VCFHeader header=(VCFHeader)codeIn.readHeader(in);
		String line;
		VCFHeader h2=new VCFHeader(header.getMetaDataInInputOrder(),header.getSampleNamesInOrder());
		w.writeHeader(h2);
		while((line=in.readLine())!=null)
			{
			VariantContext ctx=codeIn.decode(line);
			Set<String> annotations=new HashSet<String>();
			String line2;
			TabixReader.Iterator iter=tabix.query(ctx.getChr()+":"+ctx.getStart()+"-"+ctx.getEnd());
			while(iter!=null && (line2=iter.next())!=null)
				{
				String tokens[]=tab.split(line2);
				String newannot=parsedFormat.toString(tokens);
				if(!newannot.isEmpty())
					annotations.add(newannot);
				}
			if(annotations.isEmpty())
				{
				w.add(ctx);
				continue;
				}
			VariantContextBuilder vcb=new VariantContextBuilder(ctx);
			vcb.attribute(TAG, annotations);
			w.add(vcb.make());
			}
		tabix.close();
		}
	
	public static void main(String[] args) throws Exception
		{
		new VCFBed().instanceMainWithExit(args);
		}
}
