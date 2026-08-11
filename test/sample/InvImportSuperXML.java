/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package invimportxml;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

/**
 *
 * @author KhowTan81
 */
public class InvImportSuperXML {
	private static final boolean $debug = false;
	private static final char[] cursor_progress={'/','|','\\','-'}; 
	private static final String $newline = "<br>";
	private static final String $valueDQuote = "&#34;";
	private static final String $valueSQuote = "&#39;";
	
    public int TotalReceipt = 0;
    public int TotalProduct = 0;
	
	protected String CharCode = "UTF-8";
	protected String source = "";
	
	private static final int $sizeCB = 1024*1024;
	protected char[] cb = new char[$sizeCB];//ByteBuffer
	protected StringBuffer sb = new StringBuffer();
	protected int sicb = 0;//size in ByteBuffer
	protected int at_pointer;
	protected int pointer = 0;
	protected static callback[] TOC_1 = new callback[256]; // <[?] if tag
	protected static callback[] TOC_2 = new callback[256]; // <tag[?] looking for end tag
	protected static callback[] TOC_3 = new callback[256]; // <tag ["] looking for Dqoute
	protected static callback[] TOC_4 = new callback[256]; // <tag ['] looking for Sqoute

	
	private static final int $sizeStackXML = 64;
	protected char[] stackXML = new char[$sizeStackXML];
	protected int[] stackXML_l = new int[$sizeStackXML];
	protected int stackXML_p = -1,stackXML_pMax = -1;
	protected boolean flagWarning = false;
	protected boolean flagError = false;
	
	
	protected boolean eof = true;
	protected boolean eot = true;
	
	private ArrayList<String> pathRegs = new ArrayList<String>();
	private ArrayList<callback> actions = new ArrayList<callback>();

	public InvImportSuperXML(){
		sicb = 0;
		pointer = 0;
		stackXML_p = 0;
		stackXML[0] = '0';
		pathRegs.clear();
		actions.clone();
		// anything following < is the tag-opening
		for (int i=255;i>=0;i--) {
			TOC_1[i] = $TOC_1_tag;
		}
		// except this list is not the tag
		TOC_1['/'&0xff] = $TOC_1_slash;
		TOC_1['<'&0xff] = $TOC_1_lt;
		TOC_1['?'&0xff] = $TOC_1_quest;
		TOC_1['!'&0xff] = $TOC_1_ext;
		TOC_1['>'&0xff] = $TOC_1_ignor;
		TOC_1['='&0xff] = $TOC_1_ignor;
		TOC_1[' '&0xff] = $TOC_1_ignor;
		TOC_1['\t'&0xff] = $TOC_1_ignor;
		TOC_1['\n'&0xff] = $TOC_1_ignor;
		
		// inner of tag anything is tagname by default
		for (int i=255;i>=0;i--) {
			TOC_2[i] = $TOC_2_isTag;
		}
		// except this list is must care
		TOC_2['>'&0xff] = $TOC_2_endtag; 
		TOC_2['/'&0xff] = $TOC_2_slash; 
		TOC_2[' '&0xff] = $TOC_2_space;
		TOC_2['\t'&0xff] = $TOC_2_space;
		TOC_2['\r'&0xff] = $TOC_2_newline;
		TOC_2['\n'&0xff] = $TOC_2_newline;
		TOC_2['"'&0xff] = $TOC_2_Dqoute;
		TOC_2['\''&0xff] = $TOC_2_Sqoute;

		// decision of stackXML[pointer] case Dqoute
		for (int i=255;i>=0;i--) {
			TOC_3[i] = $TOC_3_lt;
		}
		// except this list is must care
		TOC_3['"'&0xff] = $TOC_3_Dqoute;
		TOC_3['\''&0xff] = $TOC_3_Sqoute;
		TOC_3['>'&0xff] = $TOC_3_gt;
		
		// decision of stackXML[pointer] case Sqoute
		for (int i=255;i>=0;i--) {
			TOC_4[i] = $TOC_4_lt;
		}
		// except this list is must care
		TOC_4['"'&0xff] = $TOC_4_Dqoute;
		TOC_4['\''&0xff] = $TOC_4_Sqoute;
		TOC_4['>'&0xff] = $TOC_4_gt;
		
		root.nameTag = null;
		root.parent = root;
		root.action = $receipt;
		regist("rsm:SupplyChainTradeTransaction/ram:IncludedSupplyChainTradeLineItem",$product);

		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/ram:SpecifiedTaxRegistration/ram:ID",$Seller);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:BuyerTradeParty/ram:SpecifiedTaxRegistration/ram:ID",$Buyer);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/ram:Name",$nameSeller);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:BuyerTradeParty/ram:Name",$nameBuyer);
		regist("rsm:ExchangedDocument/ram:TypeCode",$TypeCode);
		regist("rsm:ExchangedDocument/ram:Name",$Name);
		regist("rsm:ExchangedDocument/ram:ID",$ID);
		regist("rsm:ExchangedDocument/ram:IssueDateTime",$ExIssueDateTime);
		regist("rsm:ExchangedDocument/ram:IncludedNote",$IncludedNote);
		regist("rsm:ExchangedDocument/ram:IncludedNote/ram:Content",$IncludedNoteContent);
		regist("rsm:ExchangedDocument/ram:IncludedNote/ram:Subject",$IncludedNoteSubject);
		regist("rsm:ExchangedDocument/ram:Purpose",$Purpose);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:AdditionalReferencedDocument/ram:IssuerAssignedID",$IssuerAssignedID);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:AdditionalReferencedDocument/ram:IssueDateTime",$SCIssueDateTime);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:AdditionalReferencedDocument/ram:ReferenceTypeCode",$ReferenceTypeCode);

		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/ram:PostalTradeAddress/ram:BuildingName",$SBuildingName);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/ram:PostalTradeAddress/ram:BuildingNumber",$SBuildingNumber);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/ram:PostalTradeAddress/ram:LineThree",$SLineThree);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/ram:PostalTradeAddress/ram:LineFour",$SLineFour);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/ram:PostalTradeAddress/ram:LineFive",$SLineFive);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/ram:PostalTradeAddress/ram:StreetName",$SStreetName);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/ram:PostalTradeAddress/ram:CitySubDivisionName",$SCitySubDivisionName);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/ram:PostalTradeAddress/ram:CityName",$SCityName);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/ram:PostalTradeAddress/ram:CountrySubDivisionID",$SCountrySubDivisionID);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/ram:PostalTradeAddress/ram:PostcodeCode",$SPostcodeCode);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/ram:PostalTradeAddress/ram:LineOne",$SLineOne);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/ram:PostalTradeAddress/ram:LineTwo",$SLineTwo);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/ram:DefinedTradeContact/ram:EmailURIUniversalCommunication/ram:URIID",$SURIID);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:SellerTradeParty/ram:DefinedTradeContact/ram:TelephoneUniversalCommunication/ram:CompleteNumber",$SCompleteNumber);

		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:BuyerTradeParty/ram:PostalTradeAddress/ram:BuildingName",$BBuildingName);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:BuyerTradeParty/ram:PostalTradeAddress/ram:BuildingNumber",$BBuildingNumber);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:BuyerTradeParty/ram:PostalTradeAddress/ram:LineThree",$BLineThree);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:BuyerTradeParty/ram:PostalTradeAddress/ram:LineFour",$BLineFour);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:BuyerTradeParty/ram:PostalTradeAddress/ram:LineFive",$BLineFive);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:BuyerTradeParty/ram:PostalTradeAddress/ram:StreetName",$BStreetName);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:BuyerTradeParty/ram:PostalTradeAddress/ram:CitySubDivisionName",$BCitySubDivisionName);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:BuyerTradeParty/ram:PostalTradeAddress/ram:CityName",$BCityName);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:BuyerTradeParty/ram:PostalTradeAddress/ram:CountrySubDivisionID",$BCountrySubDivisionID);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:BuyerTradeParty/ram:PostalTradeAddress/ram:PostcodeCode",$BPostcodeCode);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:BuyerTradeParty/ram:PostalTradeAddress/ram:LineOne",$BLineOne);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:BuyerTradeParty/ram:PostalTradeAddress/ram:LineTwo",$BLineTwo);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:BuyerTradeParty/ram:DefinedTradeContact/ram:EmailURIUniversalCommunication/ram:URIID",$BURIID);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement/ram:BuyerTradeParty/ram:DefinedTradeContact/ram:TelephoneUniversalCommunication/ram:CompleteNumber",$BCompleteNumber);
		
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeSettlement/ram:SpecifiedTradeSettlementHeaderMonetarySummation/ram:OriginalInformationAmount",$OriginalInformationAmount);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeSettlement/ram:SpecifiedTradeSettlementHeaderMonetarySummation/ram:LineTotalAmount",$LineTotalAmount);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeSettlement/ram:SpecifiedTradeSettlementHeaderMonetarySummation/ram:DifferenceInformationAmount",$DifferenceInformationAmount);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeSettlement/ram:SpecifiedTradeSettlementHeaderMonetarySummation/ram:AllowanceTotalAmount",$AllowanceTotalAmount);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeSettlement/ram:SpecifiedTradeSettlementHeaderMonetarySummation/ram:ChargeTotalAmount",$ChargeTotalAmount);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeSettlement/ram:SpecifiedTradeSettlementHeaderMonetarySummation/ram:TaxBasisTotalAmount",$TaxBasisTotalAmount);

		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeSettlement/ram:ApplicableTradeTax",$ApplicableTradeTax);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeSettlement/ram:ApplicableTradeTax/ram:TypeCode",$VatTypeCode);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeSettlement/ram:ApplicableTradeTax/ram:CalculatedRate",$VatRate);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeSettlement/ram:ApplicableTradeTax/ram:BasisAmount",$BasisAmount);

		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeSettlement/ram:SpecifiedTradeSettlementHeaderMonetarySummation/ram:TaxTotalAmount",$TaxTotalAmount);
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeSettlement/ram:SpecifiedTradeSettlementHeaderMonetarySummation/ram:GrandTotalAmount",$GrandTotalAmount);		
		
		regist("rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeSettlement/ram:SpecifiedTradeAllowanceCharge/ram:ActualAmount",$AActualAmount);
        
		regist("rsm:SupplyChainTradeTransaction/ram:IncludedSupplyChainTradeLineItem/ram:AssociatedDocumentLineDocument/ram:LineID",$PLineID);
		regist("rsm:SupplyChainTradeTransaction/ram:IncludedSupplyChainTradeLineItem/ram:SpecifiedTradeProduct/ram:Name",$PName);
		regist("rsm:SupplyChainTradeTransaction/ram:IncludedSupplyChainTradeLineItem/ram:SpecifiedLineTradeDelivery/ram:BilledQuantity",$PBilledQuantity);
		regist("rsm:SupplyChainTradeTransaction/ram:IncludedSupplyChainTradeLineItem/ram:SpecifiedLineTradeAgreement/ram:GrossPriceProductTradePrice/ram:ChargeAmount",$PChargeAmount);
		regist("rsm:SupplyChainTradeTransaction/ram:IncludedSupplyChainTradeLineItem/ram:SpecifiedLineTradeAgreement/ram:GrossPriceProductTradePrice/ram:AppliedTradeAllowanceCharge/ram:ActualAmount",$PActualAmount);
		regist("rsm:SupplyChainTradeTransaction/ram:IncludedSupplyChainTradeLineItem/ram:SpecifiedLineTradeSettlement/ram:SpecifiedTradeSettlementLineMonetarySummation/ram:NetLineTotalAmount",$PNetLineTotalAmount);
		regist("rsm:SupplyChainTradeTransaction/ram:IncludedSupplyChainTradeLineItem/ram:SpecifiedLineTradeSettlement/ram:SpecifiedTradeSettlementLineMonetarySummation/ram:TaxTotalAmount",$PTaxTotalAmount);
		regist("rsm:SupplyChainTradeTransaction/ram:IncludedSupplyChainTradeLineItem/ram:SpecifiedLineTradeSettlement/ram:SpecifiedTradeSettlementLineMonetarySummation/ram:NetIncludingTaxesLineTotalAmount",$PNetIncludingTaxesLineTotalAmount);
		regist("rsm:SupplyChainTradeTransaction/ram:IncludedSupplyChainTradeLineItem/ram:SpecifiedLineTradeSettlement/ram:ApplicableTradeTax/ram:CalculatedRate",$PCalculatedRate);
	}
	
	private String[] _receipt = new String[60];
	private String[] _product = new String[14];
	private StringBuffer row = new StringBuffer();	
	private static final callback $receipt = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (args[2].equals(me.$init)){
				for (int c=me._receipt.length-1;c>=0;c-- )me._receipt[c] = null;
				for (int c=me._product.length-1;c>=0;c-- )me._product[c] = null;
				me._SschemeID = "";
				me._BschemeID = "";
				me._receipt[58]=String.valueOf((long)(0x7FFFFFFFFFFFFFFFL * Math.random()));
				me._product[1]=me._receipt[58];
				me._receipt[0]=me._nameFile;
				me._product[13]=me._nameFile;
				me._receipt[48]="";//prepared for condition 
				me._receipt[49]="";//prepared for condition
				me._receipt[57]=me.source;
				me._Content = null;
				me._Subject = null;
				me._txtSubject = false;
				me._VatTypeCode = null;
				me._VatRate = null;
				me._BasisAmount = null;
			}else if (args[2].equals(me.$end)) {
				// trim & clean
				for (int c=0;c<me._receipt.length-2;c++) me._receipt[c] = me.Clean(me._receipt[c]);
				// replace empty with zero here
//				39	OLD_AMOUNT	DECIMAL	20
				if (null == me._receipt[39] || "".equals(me._receipt[39].replaceAll("^ +| +$|( )+", "$1"))) {me._receipt[39]="0";};
//				40	AMOUNT	DECIMAL	20
				if (null == me._receipt[40] || "".equals(me._receipt[40].replaceAll("^ +| +$|( )+", "$1"))) {me._receipt[40]="0";};
//				41	DIFF	DECIMAL	20
				if (null == me._receipt[41] || "".equals(me._receipt[41].replaceAll("^ +| +$|( )+", "$1"))) {me._receipt[41]="0";};
//				42	DISCOUNT	DECIMAL	20
				if (null == me._receipt[42] || "".equals(me._receipt[42].replaceAll("^ +| +$|( )+", "$1"))) {me._receipt[42]="0";};
//				43	SERVICE	DECIMAL	20
				if (null == me._receipt[43] || "".equals(me._receipt[43].replaceAll("^ +| +$|( )+", "$1"))) {me._receipt[43]="0";};
//				44	TOTAL_AMOUNT	DECIMAL	20
				if (null == me._receipt[44] || "".equals(me._receipt[44].replaceAll("^ +| +$|( )+", "$1"))) {me._receipt[44]="0";};
//				47	BASIS_AMOUNT_VAT7	DECIMAL	20
				if (null == me._receipt[47] || "".equals(me._receipt[47].replaceAll("^ +| +$|( )+", "$1"))) {me._receipt[47]="0";};
//				48	BASIS_AMOUNT_VAT0	DECIMAL	20
				if (null == me._receipt[48] || "".equals(me._receipt[48].replaceAll("^ +| +$|( )+", "$1"))) {me._receipt[48]="0";};
//				49	BASIS_AMOUNT_FREE	DECIMAL	20
				if (null == me._receipt[49] || "".equals(me._receipt[49].replaceAll("^ +| +$|( )+", "$1"))) {me._receipt[49]="0";};
//				50	VAT_AMOUNT	DECIMAL	20
				if (null == me._receipt[50] || "".equals(me._receipt[50].replaceAll("^ +| +$|( )+", "$1"))) {me._receipt[50]="0";};
//				51	GRANDTOTAL_AMOUNT	DECIMAL	20
				if (null == me._receipt[51] || "".equals(me._receipt[51].replaceAll("^ +| +$|( )+", "$1"))) {me._receipt[51]="0";};
//				46	VAT	DECIMAL	4
				if (null == me._receipt[46] || "".equals(me._receipt[46].replaceAll("^ +| +$|( )+", "$1"))) {me._receipt[46]="0";};
				
				// check data-type here
//				37	DOC_NAME	VARCHAR	122	35	แก้ขนาด 2019-04-24
//				38	NUM_REC	VARCHAR	102	35	แก้ขนาด 2019-04-24
//				53	CONTENT	VARCHAR	252	500	แก้ขนาด 2019-04-24
//				54	PURPOSE	VARCHAR	252	256	แก้ขนาด 2019-04-24
//				55	NUM_REC_OLD	VARCHAR	102	35	แก้ขนาด 2019-04-24
				
				if (null==me._receipt[59]){
//					0	XML_FILENAME	VARCHAR	100
//					E0200 : XML_FILENAME IS TOO LONG
					if (null != me._receipt[0] && 100 < me._receipt[0].length()) {
						me._receipt[59] = "E0200";
					}
					
//					2	NID	VARCHAR	15	35	แก้ขนาด 2019-04-24
//					E0501 : Seller.NID is null					
					else if (null == me._receipt[1]) {
						me._receipt[59] = "E0501";
					}
//					E0201 : NID IS TOO LONG
					else if (35 < me._receipt[1].length()) {
						me._receipt[59] = "E0201";
					}
					
//					3	NAME	VARCHAR	252	256	แก้ขนาด 2019-04-24
//					E0202 : NAME IS TOO LONG
					else if (null != me._receipt[2] && 256 < me._receipt[2].length()) {
						me._receipt[59] = "E0202";
					}
					
//					5	BUILDNAME	VARCHAR	102	70	แก้ขนาด 2019-04-24
//					E0204 : BUILDNAME IS TOO LONG
					else if (null != me._receipt[4] && 70 < me._receipt[4].length()) {
						me._receipt[59] = "E0204";
					}
					
//					6	ADDNO	VARCHAR	22	16	แก้ขนาด 2019-04-24
//					E0205 : ADDNO IS TOO LONG
					else if (null != me._receipt[5] && 16 < me._receipt[5].length()) {
						me._receipt[59] = "E0205";
					}
					
//					7	SOI	VARCHAR	52	70	แก้ขนาด 2019-04-24
//					E0206 : SOI IS TOO LONG
					else if (null != me._receipt[6] && 70 < me._receipt[6].length()) {
						me._receipt[59] = "E0206";
					}
					
//					8	MOONAM	VARCHAR	52	70	แก้ขนาด 2019-04-24
//					E0207 : MOONAM IS TOO LONG
					else if (null != me._receipt[7] && 70 < me._receipt[7].length()) {
						me._receipt[59] = "E0207";
					}
					
//					9	MOO	VARCHAR	22	70	แก้ขนาด 2019-04-24
//					E0208 : MOO IS TOO LONG
					else if (null != me._receipt[8] && 70 < me._receipt[8].length()) {
						me._receipt[59] = "E0208";
					}
					
//					10	THNNAM	VARCHAR	52	70	แก้ขนาด 2019-04-24
//					E0209 : THNNAM IS TOO LONG
					else if (null != me._receipt[9] && 70 < me._receipt[9].length()) {
						me._receipt[59] = "E0209";
					}
					
//					11	TAMNAM	VARCHAR	52	70	แก้ขนาด 2019-04-24
//					E0210 : TAMNAM IS TOO LONG
					else if (null != me._receipt[10] && 70 < me._receipt[10].length()) {
						me._receipt[59] = "E0210";
					}
					
//					12	AMPER	VARCHAR	52	70	แก้ขนาด 2019-04-24
//					E0211 : AMPER IS TOO LONG
					else if (null != me._receipt[11] && 70 < me._receipt[11].length()) {
						me._receipt[59] = "E0211";
					}
					
//					13	PROVNAM	VARCHAR	52	35	แก้ขนาด 2019-04-24
//					E0212 : PROVNAM IS TOO LONG
					else if (null != me._receipt[12] && 35 < me._receipt[12].length()) {
						me._receipt[59] = "E0212";
					}
					
//					14	POSCOD	VARCHAR	7	16	แก้ขนาด 2019-04-24
//					E0213 : POSCOD IS TOO LONG
					else if (null != me._receipt[13] && 16 < me._receipt[13].length()) {
						me._receipt[59] = "E0213";
					}
					
//					15	ADDRESS1	VARCHAR	252	256	แก้ขนาด 2019-04-24
//					E0214 : ADDRESS1 IS TOO LONG
					else if (null != me._receipt[14] && 256 < me._receipt[14].length()) {
						me._receipt[59] = "E0214";
					}
					
//					16	ADDRESS2	VARCHAR	252	256	แก้ขนาด 2019-04-24
//					E0215 : ADDRESS2 IS TOO LONG
					else if (null != me._receipt[15] && 256 < me._receipt[15].length()) {
						me._receipt[59] = "E0215";
					}
					
//					17	EMAIL	VARCHAR	102	256	แก้ขนาด 2019-04-24
//					E0216 : EMAIL IS TOO LONG
					else if (null != me._receipt[16] && 256 < me._receipt[16].length()) {
						me._receipt[59] = "E0216";
					}
					
//					18	PHONE	VARCHAR	52	35	แก้ขนาด 2019-04-24
//					E0217 : PHONE IS TOO LONG
					else if (null != me._receipt[17] && 35 < me._receipt[17].length()) {
						me._receipt[59] = "E0217";
					}
					
//					19	NID_B	VARCHAR	15	35	แก้ขนาด 2019-04-24
//					E0218 : NID_B IS TOO LONG
					else if (null != me._receipt[18] && 35 < me._receipt[18].length()) {
						me._receipt[59] = "E0218";
					}
					
//					20	NAME_B	VARCHAR	252	256	แก้ขนาด 2019-04-24
//					E0219 : NAME_B IS TOO LONG
					else if (null != me._receipt[19] && 256 < me._receipt[19].length()) {
						me._receipt[59] = "E0219";
					}
					
//					22	BUILDNAME_B	VARCHAR	52	70	แก้ขนาด 2019-04-24
//					E0221 : BUILDNAME_B IS TOO LONG
					else if (null != me._receipt[21] && 70 < me._receipt[21].length()) {
						me._receipt[59] = "E0221";
					}
					
//					23	ADDNO_B	VARCHAR	22	16	แก้ขนาด 2019-04-24
//					E0222 : ADDNO_B IS TOO LONG
					else if (null != me._receipt[22] && 16 < me._receipt[22].length()) {
						me._receipt[59] = "E0222";
					}
					
//					24	SOI_B	VARCHAR	52	70	แก้ขนาด 2019-04-24
//					E0223 : SOI_B IS TOO LONG
					else if (null != me._receipt[23] && 70 < me._receipt[23].length()) {
						me._receipt[59] = "E0223";
					}
					
//					25	MOONAM_B	VARCHAR	52	70	แก้ขนาด 2019-04-24
//					E0224 : MOONAM_B IS TOO LONG
					else if (null != me._receipt[24] && 70 < me._receipt[24].length()) {
						me._receipt[59] = "E0224";
					}
					
//					26	MOO_B	VARCHAR	22	70	แก้ขนาด 2019-04-24
//					E0225 : MOO_B IS TOO LONG
					else if (null != me._receipt[25] && 70 < me._receipt[25].length()) {
						me._receipt[59] = "E0225";
					}
					
//					27	THNNAM_B	VARCHAR	52	70	แก้ขนาด 2019-04-24
//					E0226 : THNNAM_B IS TOO LONG
					else if (null != me._receipt[26] && 70 < me._receipt[26].length()) {
						me._receipt[59] = "E0226";
					}
					
//					28	TAMNAM_B	VARCHAR	52	70	แก้ขนาด 2019-04-24
//					E0227 : TAMNAM_B IS TOO LONG
					else if (null != me._receipt[27] && 70 < me._receipt[27].length()) {
						me._receipt[59] = "E0227";
					}
					
//					29	AMPER_B	VARCHAR	52	70	แก้ขนาด 2019-04-24
//					E0228 : AMPER_B IS TOO LONG
					else if (null != me._receipt[28] && 70 < me._receipt[28].length()) {
						me._receipt[59] = "E0228";
					}
					
//					30	PROVNAM_B	VARCHAR	52	35	แก้ขนาด 2019-04-24
//					E0229 : PROVNAM_B IS TOO LONG
					else if (null != me._receipt[29] && 35 < me._receipt[29].length()) {
						me._receipt[59] = "E0229";
					}
					
//					31	POSCOD_B	VARCHAR	7	16	แก้ขนาด 2019-04-24
//					E0230 : POSCOD_B IS TOO LONG
					else if (null != me._receipt[30] && 16 < me._receipt[30].length()) {
						me._receipt[59] = "E0230";
					}
					
//					32	ADDRESS1_B	VARCHAR	252	256	แก้ขนาด 2019-04-24
//					E0231 : ADDRESS1_B IS TOO LONG
					else if (null != me._receipt[31] && 256 < me._receipt[31].length()) {
						me._receipt[59] = "E0231";
					}
					
//					33	ADDRESS2_B	VARCHAR	252	256	แก้ขนาด 2019-04-24
//					E0232 : ADDRESS2_B IS TOO LONG
					else if (null != me._receipt[32] && 256 < me._receipt[32].length()) {
						me._receipt[59] = "E0232";
					}
					
//					34	EMAIL_B	VARCHAR	102	256	แก้ขนาด 2019-04-24
//					E0233 : EMAIL_B IS TOO LONG
					else if (null != me._receipt[33] && 256 < me._receipt[33].length()) {
						me._receipt[59] = "E0233";
					}
					
//					35	PHONE_B	VARCHAR	52	35	แก้ขนาด 2019-04-24
//					E0234 : PHONE_B IS TOO LONG
					else if (null != me._receipt[34] && 35 < me._receipt[34].length()) {
						me._receipt[59] = "E0234";
					}
					
//					35	DOC_TYPE	VARCHAR	50
//					E0235 : DOC_TYPE IS TOO LONG
					else if (null != me._receipt[35] && 50 < me._receipt[35].length()) {
						me._receipt[59] = "E0235";
					}
					
//					37	DOC_NAME	VARCHAR	122	35	แก้ขนาด 2019-04-24
//					E0236 : DOC_NAME IS TOO LONG
					else if (null != me._receipt[36] && 35 < me._receipt[36].length()) {
						me._receipt[59] = "E0236";
					}

//					38	NUM_REC	VARCHAR	102	35	แก้ขนาด 2019-04-24
//					E0237 : NUM_REC IS TOO LONG
					else if (null != me._receipt[37] && 35 < me._receipt[37].length()) {
						me._receipt[59] = "E0237";
					}
					
//					38	DATE	VARCHAR	30
//					E0238 : DATE IS TOO LONG
					else if (null != me._receipt[38] && 30 < me._receipt[38].length()) {
						me._receipt[59] = "E0238";
					}
					
//					45	TAX_REC	VARCHAR	50
//					E0245 : TAX_REC IS TOO LONG
					else if (null != me._receipt[45] && 50 < me._receipt[45].length()) {
						me._receipt[59] = "E0245";
					}
					
//					53	CONTENT	VARCHAR	252	500	แก้ขนาด 2019-04-24
//					E0252 : CONTENT IS TOO LONG
					else if (null != me._receipt[52] && 500 < me._receipt[52].length()) {
						me._receipt[59] = "E0252";
					}
					
//					54	PURPOSE	VARCHAR	252	256	แก้ขนาด 2019-04-24
//					E0253 : PURPOSE IS TOO LONG
					else if (null != me._receipt[53] && 256 < me._receipt[53].length()) {
						me._receipt[59] = "E0253";
					}

//					55	NUM_REC_OLD	VARCHAR	102	35	แก้ขนาด 2019-04-24
//					E0254 : NUM_REC_OLD IS TOO LONG
					else if (null != me._receipt[54] && 35 < me._receipt[54].length()) {
						me._receipt[59] = "E0254";
					}
					
//					55	DATE_OLD	VARCHAR	30
//					E0255 : DATE_OLD IS TOO LONG
					else if (null != me._receipt[55] && 30 < me._receipt[55].length()) {
						me._receipt[59] = "E0255";
					}
					
//					56	DOC_TYPE_OLD	VARCHAR	50
//					E0256 : DOC_TYPE_OLD IS TOO LONG
					else if (null != me._receipt[56] && 50 < me._receipt[56].length()) {
						me._receipt[59] = "E0256";
					}
					
//					57	DATASOURCE	VARCHAR	10					
//					E0257 : DATASOURCE IS TOO LONG
					else if (null != me._receipt[57] && 10 < me._receipt[57].length()) {
						me._receipt[59] = "E0257";
					}
					
//					39	OLD_AMOUNT	DECIMAL	20
//					E0139 : TRANSLATE MUST BE NUMERIC
					else if (!isNumeric(me._receipt[39])) {
						me._receipt[59] = "E0139";
					}

//					40	AMOUNT	DECIMAL	20
//					E0140 : AMOUNT MUST BE NUMERIC
					else if (!isNumeric(me._receipt[40])) {
						me._receipt[59] = "E0140";
					}
					
//					41	DIFF	DECIMAL	20
//					E0141 : DIFF MUST BE NUMERIC
					else if (!isNumeric(me._receipt[41])) {
						me._receipt[59] = "E0141";
					}
					
//					42	DISCOUNT	DECIMAL	20
//					E0142 : DISCOUNT MUST BE NUMERIC
					else if (!isNumeric(me._receipt[42])) {
						me._receipt[59] = "E0142";
					}
					
//					43	SERVICE	DECIMAL	20
//					E0143 : SERVICE MUST BE NUMERIC
					else if (!isNumeric(me._receipt[43])) {
						me._receipt[59] = "E0143";
					}
					
//					44	TOTAL_AMOUNT	DECIMAL	20
//					E0144 : TOTAL_AMOUNT MUST BE NUMERIC
					else if (!isNumeric(me._receipt[44])) {
						me._receipt[59] = "E0144";
					}
					
//					46	VAT	DECIMAL	4
//					E0146 : VAT MUST BE NUMERIC
					else if (!isNumeric(me._receipt[46])) {
						me._receipt[59] = "E0146";
					}
					else if (!isVatRate(me._receipt[46])) {
						me._receipt[59] = "E0646";
					}
					
//					47	BASIS_AMOUNT_VAT7	DECIMAL	20
//					E0147 : BASIS_AMOUNT_VAT7 MUST BE NUMERIC
					else if (!isNumeric(me._receipt[47])) {
						me._receipt[59] = "E0147";
					}
					
//					48	BASIS_AMOUNT_VAT0	DECIMAL	20
//					E0148 : BASIS_AMOUNT_VAT0 MUST BE NUMERIC
					else if (!isNumeric(me._receipt[48])) {
						me._receipt[59] = "E0148";
					}
					
//					49	BASIS_AMOUNT_FREE	DECIMAL	20
//					E0149 : BASIS_AMOUNT_FREE MUST BE NUMERIC
					else if (!isNumeric(me._receipt[49])) {
						me._receipt[59] = "E0149";
					}
					
//					50	VAT_AMOUNT	DECIMAL	20
//					E0150 : VAT_AMOUNT MUST BE NUMERIC
					else if (!isNumeric(me._receipt[50])) {
						me._receipt[59] = "E0150";
					}
					
//					51	GRANDTOTAL_AMOUNT	DECIMAL	20
//					E0151 : GRANDTOTAL_AMOUNT MUST BE NUMERIC
					else if (!isNumeric(me._receipt[51])) {
						me._receipt[59] = "E0151";
					}
					
//					4	BRANCH	VARCHAR	7	5	แก้ขนาด 2019-04-24
//					E0203 : BRANCH IS TOO LONG
//					E0103 : BRANCH MUST BE POSITIVE NUMERIC
					else if (!me._receipt[3].matches("\\d{1,5}")) {
						me._receipt[59] = "E0103";
					}
					
//					21	BRANCH_B	VARCHAR	7	5	แก้ขนาด 2019-04-24
//					E0220 : BRANCH_B IS TOO LONG
//					E0120 : BRANCH_B MUST BE POSITIVE NUMERIC
					else if (!me._receipt[20].matches("\\d{0,5}")) {
						me._receipt[59] = "E0120";
					}
					
					// acceptable data
					else {
						me._receipt[59] = "#";
					}
					
				} 
				// flush data
				me.row.delete(0, me.row.length());
				for (int c=0;c<me._receipt.length-2;c++) me.row.append(","+me.Value(me._receipt[c]));
				me.row.append(","+me._receipt[me._receipt.length-2]); //ID_RANDOM
				me.row.append(","+me.Value(me._receipt[me._receipt.length-1])); //DELETE1
				me.row.append('\n');
				me._OSR.setMsg(me.row.substring(1));
				me.row.delete(0, me.row.length());
				me.TotalReceipt++;
				if ((me.TotalReceipt&0x3FF)==0) System.out.print("\b"+cursor_progress[(int)((me.TotalReceipt>>10) & 3)]);
			}
			return true;
		}
	};
	
	private static final callback $product = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (args[2].equals(me.$init)){
				for (int c=12;c>1;c-- )me._product[c] = null;
				// ID_RANDOM
				me._product[0] = String.valueOf((long)(0x7FFFFFFFFFFFFFFFL * Math.random()));
			}else if (args[2].equals(me.$end)) {
				// trim & clean
				for (int c=12;c>1;c--) me._product[c] = me.Clean(me._product[c]);
				// replace empty with zero here
//				4	QUANTITY	DECIMAL	20
				if (null == me._product[4] || "".equals(me._product[4].replaceAll("^ +| +$|( )+", "$1"))) {me._product[4]="0";};
//				6	PRICE	DECIMAL	20
				if (null == me._product[6] || "".equals(me._product[6].replaceAll("^ +| +$|( )+", "$1"))) {me._product[6]="0";};
//				7	DISCOUNT_ITEM	DECIMAL	20
				if (null == me._product[7] || "".equals(me._product[7].replaceAll("^ +| +$|( )+", "$1"))) {me._product[7]="0";};
//				8	DISCOUNT	DECIMAL	20
				if (null == me._product[8] || "".equals(me._product[8].replaceAll("^ +| +$|( )+", "$1"))) {me._product[8]="0";};
//				9	AMOUNT	DECIMAL	20
				if (null == me._product[9] || "".equals(me._product[9].replaceAll("^ +| +$|( )+", "$1"))) {me._product[9]="0";};
//				10	VAT_TOTAL	DECIMAL	20
				if (null == me._product[10] || "".equals(me._product[10].replaceAll("^ +| +$|( )+", "$1"))) {me._product[10]="0";};
//				11	TOTAL_AMOUNT	DECIMAL	20
				if (null == me._product[11] || "".equals(me._product[11].replaceAll("^ +| +$|( )+", "$1"))) {me._product[11]="0";};
//				12	VAT_RATE	DECIMAL	4
				if (null == me._product[12] || "".equals(me._product[12].replaceAll("^ +| +$|( )+", "$1"))) {me._product[12]="0";};
				
				// check data-type here
				if (null==me._receipt[59]){
//				2	,	SEQ	VARCHAR	(	20	)
//				E0407 : PRODUCT_DESC_TEMP.SEQ  IS TOO LONG
					if (null != me._product[2] && 20 < me._product[2].length()) {
						me._receipt[59] = "E0407";
					}
				
//				3	,	PRODUCT_NAME	VARCHAR	(	350	)
//				E0408 : PRODUCT_DESC_TEMP.NAME  IS TOO LONG
					else if (null != me._product[3] && 350 < me._product[3].length()) {
						me._receipt[59] = "E0408";
					}

//				5	,	UNIT	VARCHAR	(	20	)
//				E0410 : PRODUCT_DESC_TEMP.UNIT  IS TOO LONG
					else if (null != me._product[5] && 20 < me._product[5].length()) {
						me._receipt[59] = "E0410";
					}
				
//				4	,	QUANTITY	DECIMAL	(	20	)
//				E0309 : PRODUCT_DESC_TEMP.QUANTITY MUST BE NUMERIC
					else if (!isNumeric(me._product[4])) {
						me._receipt[59] = "E0309";
					}

//				6	,	PRICE	DECIMAL	(	20	)
//				E0311 : PRODUCT_DESC_TEMP.PRICE MUST BE NUMERIC
					else if (!isNumeric(me._product[6])) {
						me._receipt[59] = "E0311";
					}
				
//				7	,	DISCOUNT_ITEM	DECIMAL	(	20	)
//				E0312 : PRODUCT_DESC_TEMP.DISCOUNT_ITEM MUST BE NUMERIC
					else if (!isNumeric(me._product[7])) {
						me._receipt[59] = "E0312";
					}
				
//				8	,	DISCOUNT	DECIMAL	(	20	)
//				E0313 : PRODUCT_DESC_TEMP.DISCOUNT MUST BE NUMERIC
					else if (!isNumeric(me._product[8])) {
						me._receipt[59] = "E0313";
					}
				
//				9	,	AMOUNT	DECIMAL	(	20	)
//				E0314 : PRODUCT_DESC_TEMP.AMOUNT MUST BE NUMERIC
					else if (!isNumeric(me._product[9])) {
						me._receipt[59] = "E0314";
					}
				
//				10	,	VAT_TOTAL	DECIMAL	(	20	)
//				E0315 : PRODUCT_DESC_TEMP.VAT_TOTAL MUST BE NUMERIC
					else if (!isNumeric(me._product[10])) {
						me._receipt[59] = "E0315";
					}
				
//				11	,	TOTAL_AMOUNT	DECIMAL	(	20	)
//				E0316 : PRODUCT_DESC_TEMP.TOTAL_AMOUNT MUST BE NUMERIC
					else if (!isNumeric(me._product[11])) {
						me._receipt[59] = "E0316";
					}
				
					else {
//				12	,	VAT_RATE	DECIMAL	(	4	)
						try {
							float vat_rate = Float.valueOf(me._product[12]);
//					E0318 : PRODUCT_DESC_TEMP.VAT_RATE MUST LESS THAN 100%
							if (vat_rate > 99.99 ) {
								me._receipt[59] = "E0318";
							} else {
								me._product[12] = String.valueOf(vat_rate);
							}
						} catch (NumberFormatException ne) {
//					E0317 : PRODUCT_DESC_TEMP.VAT_RATE MUST BE NUMERIC				
							me._receipt[59] = "E0317";
						}
					}
				}
				// flush data
				me.row.delete(0, me.row.length());
				me.row.append(","+me._product[0]);
				me.row.append(","+me._product[1]);
				for (int c=2;c<me._product.length;c++) me.row.append(","+me.Value(me._product[c]));
				me.row.append('\n');
				me._OSP.setMsg(me.row.substring(1));
				me.row.delete(0, me.row.length());
				me.TotalProduct++;
			}
			return true;
		}
	};
	
	
	private static String attr(String a){
		return ('\"'==a.charAt(0)||'\''==a.charAt(0))?a.substring(1, a.length()-1):a;
	}
	
	private String _SschemeID,_BschemeID;
	private static final callback $Seller = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[1]==null){
				if (args[2].equals(me.$attr)){
					String[] attr = ((String)args[3]).split("=");
					if (attr.length>1 && attr[0].equalsIgnoreCase("schemeID")) {
						me._SschemeID = attr(attr[1]);
					}
				}else if (args[2].equals(me.$inner)){
					String id,bra="";
					String value = (String)args[3]; 
		            if ("TXID".equalsIgnoreCase(me._SschemeID) || "".equalsIgnoreCase(me._SschemeID)) {
		            	if (value.length()<=13) {
		            		id = value;
		            		bra = "";
		            		throw(new Exception("SSchemeID is TXID but data is '"+value+"'(only "+value.length()+" char) "));
		            	}else{
			            	id = value.substring(0, 13);
			            	bra = value.substring(13);
		            	}
		            }else if ("NIDN".equalsIgnoreCase(me._SschemeID)) {
		            	id = value;
		            }else if ("CCPT".equalsIgnoreCase(me._SschemeID)) {
		            	id = value;
		            }else if ("OTHR".equalsIgnoreCase(me._SschemeID)) {
		            	id = "N/A";
		            }else{
		            	id = value;
		            }
		            me._receipt[1]=id;
		            me._receipt[3]=bra;
				}
			}
			return true;
		}
	};
	
	private static final callback $Buyer = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[18]==null) {
				if (args[2].equals(me.$attr)){
					String[] attr = ((String)args[3]).split("=");
					if (attr.length>1 && attr[0].equalsIgnoreCase("schemeID")) {
						me._BschemeID = attr(attr[1]);
					}
				}else if (args[2].equals(me.$inner)){
					String id,bra="";
					String value = (String)args[3]; 
		            if ("TXID".equalsIgnoreCase(me._BschemeID) || "".equalsIgnoreCase(me._BschemeID)) {
		            	if (value.length()<=13) {
		            		id = value;
		            		bra = "";
		            		throw(new Exception("BSchemeID is TXID but data is '"+value+"'(only "+value.length()+" char) "));
		            	}else{
			            	id = value.substring(0, 13);
			            	bra = value.substring(13);
		            	}
		            }else if ("NIDN".equalsIgnoreCase(me._BschemeID)) {
		            	id = value;
		            }else if ("CCPT".equalsIgnoreCase(me._BschemeID)) {
		            	id = value;
		            }else if ("OTHR".equalsIgnoreCase(me._BschemeID)) {
		            	id = "N/A";
		            }else{
		            	id = value;
		            }
		            me._receipt[18]=id;
		            me._receipt[20]=bra;
				}
			}
			return true;
		}
	};	
	
	private static final callback $nameSeller = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[2]==null && args[2].equals(me.$inner)){
				me._receipt[2]=(String)args[3];
			}
			return true;
		}		
	};
	
	private static final callback $nameBuyer = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[19]==null && args[2].equals(me.$inner)){
				me._receipt[19]=(String)args[3];
			}
			return true;
		}		
	};
	
	private static final callback $TypeCode = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[35]==null && args[2].equals(me.$inner)){
				me._receipt[35]=(String)args[3];
			}
			return true;
		}		
	};
	
	private static final callback $Name = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[36]==null && args[2].equals(me.$inner)){
				me._receipt[36]=(String)args[3];
			}
			return true;
		}		
	};
	
	private static final callback $ID = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[37]==null && args[2].equals(me.$inner)){
				me._receipt[37]=(String)args[3];
			}
			return true;
		}		
	};
	
	private static final callback $ExIssueDateTime = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[38]==null && args[2].equals(me.$inner)){
				me._receipt[38]=(String)args[3];
			}
			return true;
		}		
	};

	private String _Content,_Subject;
	private boolean _txtSubject ;
	private static final callback $IncludedNote = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (!me._txtSubject){
				if (args[2].equals(me.$init)) {
					me._Subject = null;
					me._Content = null;
				}else  if (args[2].equals(me.$end) && (me._Subject!=null) && (me._Subject.contains("TEXT")||me._Subject.contains("TXT"))) {
					me._txtSubject = true;
					me._receipt[52] = me._Content;
				}
			}
			return true;
		}		
	};
	private static final callback $IncludedNoteContent = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (args[2].equals(me.$inner)) {
				if (!me._txtSubject) {
					me._Content = (String)args[3];
				}
				if (null==me._receipt[52]) {
					me._receipt[52] = me._Content;
				}
			}
			return true;
		}		
	};
	private static final callback $IncludedNoteSubject = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (!me._txtSubject && args[2].equals(me.$inner)) {
				me._Subject = ((String)args[3]).toUpperCase();
			}
			return true;
		}		
	};
	
	private static final callback $Purpose = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[53]==null && args[2].equals(me.$inner)){
				me._receipt[53]=(String)args[3];
			}
			return true;
		}		
	};
	
	private static final callback $IssuerAssignedID = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[54]==null && args[2].equals(me.$inner)){
				me._receipt[54]=(String)args[3];
			}
			return true;
		}		
	};
	
	private static final callback $SCIssueDateTime = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[55]==null && args[2].equals(me.$inner)){
				me._receipt[55]=(String)args[3];
			}
			return true;
		}		
	};
	
	private static final callback $ReferenceTypeCode = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[56]==null && args[2].equals(me.$inner)){
				me._receipt[56]=(String)args[3];
			}
			return true;
		}		
	};

	private static final callback $SBuildingName = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[4]==null && args[2].equals(me.$inner)){
				me._receipt[4]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $SBuildingNumber = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[5]==null && args[2].equals(me.$inner)){
				me._receipt[5]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $SLineThree = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[6]==null && args[2].equals(me.$inner)){
				me._receipt[6]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $SLineFour = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[7]==null && args[2].equals(me.$inner)){
				me._receipt[7]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $SLineFive = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[8]==null && args[2].equals(me.$inner)){
				me._receipt[8]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $SStreetName = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[9]==null && args[2].equals(me.$inner)){
				me._receipt[9]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $SCitySubDivisionName = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[10]==null && args[2].equals(me.$inner)){
				me._receipt[10]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $SCityName = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[11]==null && args[2].equals(me.$inner)){
				me._receipt[11]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $SCountrySubDivisionID = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[12]==null && args[2].equals(me.$inner)){
				me._receipt[12]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $SPostcodeCode = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[13]==null && args[2].equals(me.$inner)){
				me._receipt[13]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $SLineOne = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[14]==null && args[2].equals(me.$inner)){
				me._receipt[14]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $SLineTwo = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[15]==null && args[2].equals(me.$inner)){
				me._receipt[15]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $SURIID = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[16]==null && args[2].equals(me.$inner)){
				me._receipt[16]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $SCompleteNumber = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[17]==null && args[2].equals(me.$inner)){
				me._receipt[17]=(String)args[3];
			}
			return true;
		}		
	};
	
	private static final callback $BBuildingName = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[21]==null && args[2].equals(me.$inner)){
				me._receipt[21]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $BBuildingNumber = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[22]==null && args[2].equals(me.$inner)){
				me._receipt[22]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $BLineThree = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[23]==null && args[2].equals(me.$inner)){
				me._receipt[23]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $BLineFour = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[24]==null && args[2].equals(me.$inner)){
				me._receipt[24]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $BLineFive = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[25]==null && args[2].equals(me.$inner)){
				me._receipt[25]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $BStreetName = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[26]==null && args[2].equals(me.$inner)){
				me._receipt[26]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $BCitySubDivisionName = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[27]==null && args[2].equals(me.$inner)){
				me._receipt[27]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $BCityName = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[28]==null && args[2].equals(me.$inner)){
				me._receipt[28]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $BCountrySubDivisionID = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[29]==null && args[2].equals(me.$inner)){
				me._receipt[29]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $BPostcodeCode = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[30]==null && args[2].equals(me.$inner)){
				me._receipt[30]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $BLineOne = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[31]==null && args[2].equals(me.$inner)){
				me._receipt[31]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $BLineTwo = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[32]==null && args[2].equals(me.$inner)){
				me._receipt[32]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $BURIID = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[33]==null && args[2].equals(me.$inner)){
				me._receipt[33]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $BCompleteNumber = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[34]==null && args[2].equals(me.$inner)){
				me._receipt[34]=(String)args[3];
			}
			return true;
		}		
	};
	
	private static final callback $OriginalInformationAmount = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[39]==null && args[2].equals(me.$inner)){
				me._receipt[39]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $LineTotalAmount = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[40]==null && args[2].equals(me.$inner)){
				me._receipt[40]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $DifferenceInformationAmount = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[41]==null && args[2].equals(me.$inner)){
				me._receipt[41]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $AllowanceTotalAmount = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[42]==null && args[2].equals(me.$inner)){
				me._receipt[42]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $ChargeTotalAmount = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[43]==null && args[2].equals(me.$inner)){
				me._receipt[43]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $TaxBasisTotalAmount = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[44]==null && args[2].equals(me.$inner)){
				me._receipt[44]=(String)args[3];
			}
			return true;
		}		
	};
	
	private String _VatTypeCode,_VatRate,_BasisAmount;
	private static final callback $ApplicableTradeTax = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (args[2].equals(me.$init)) {
				me._VatTypeCode = null;
				me._VatRate = "";
				me._BasisAmount = null;
			}else  if (args[2].equals(me.$end)){
				if (!"VAT".equals(me._receipt[45]) && null!=me._VatTypeCode ) {
					me._receipt[45] = me._VatTypeCode;
				}
				if (!"".equals(me._VatRate) && me._VatRate!=null) {
					float VatRate = 0f;
					try{
						VatRate = Float.valueOf(me._VatRate);
					}catch (Exception e){
						
					}
					float receipt46 = 0f;
					try{
						receipt46 = Float.valueOf(me._receipt[46]);
					}catch (Exception e){
						
					}					
					if (VatRate>receipt46) {
						me._receipt[46] = me._VatRate;
						me._receipt[47] = me._BasisAmount;
					}
					if ((null==me._receipt[48] || "".equals(me._receipt[48]))&&("VAT".equals(me._VatTypeCode)&&VatRate==0f)){
						me._receipt[48] = me._BasisAmount;
					}
					if ((null==me._receipt[49] || "".equals(me._receipt[49]))&&"FRE".equals(me._VatTypeCode)){
						me._receipt[49] = me._BasisAmount;
					}
				}
			}
			return true;
		}		
	};
	private static final callback $VatTypeCode = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._VatTypeCode==null && args[2].equals(me.$inner)) {
				me._VatTypeCode = ((String)args[3]).toUpperCase();
			}
			return true;
		}		
	};
	private static final callback $VatRate = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if ((null==me._VatRate || "".equals(me._VatRate)) && args[2].equals(me.$inner)) {
				me._VatRate = (String)args[3];
			}
			return true;
		}		
	};
	private static final callback $BasisAmount = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._BasisAmount==null && args[2].equals(me.$inner)) {
				me._BasisAmount = (String)args[3];
			}
			return true;
		}		
	};
	
	private static final callback $TaxTotalAmount = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[50]==null && args[2].equals(me.$inner)){
				me._receipt[50]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $GrandTotalAmount = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (me._receipt[51]==null && args[2].equals(me.$inner)){
				me._receipt[51]=(String)args[3];
			}
			return true;
		}		
	};
	
	private static final callback $PLineID = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (null==me._product[2] && args[2].equals(me.$inner)){
				me._product[2]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $PName = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (null==me._product[3] && args[2].equals(me.$inner)){
				me._product[3]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $PBilledQuantity = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (null==me._product[5] && args[2].equals(me.$attr)){
				String[] attr = ((String)args[3]).split("=");
				if (attr.length>1 && attr[0].equalsIgnoreCase("unitCode")) {
					me._product[5] = attr(attr[1]);
				}
			}else if (null==me._product[4] && args[2].equals(me.$inner)){
				me._product[4]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $PChargeAmount = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (null==me._product[6] && args[2].equals(me.$inner)){
				me._product[6]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $PActualAmount = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (null==me._product[7] && args[2].equals(me.$inner)){
				me._product[7]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $AActualAmount = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (null==me._product[8] && args[2].equals(me.$inner)){
				me._product[8]=(String)args[3];
			}
			return true;
		}		
	};
	private static final callback $PNetLineTotalAmount = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (null==me._product[9] && args[2].equals(me.$inner)){
				me._product[9]=(String)args[3];
			}
			return true;
		}		
	};	
	private static final callback $PTaxTotalAmount = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (null==me._product[10] && args[2].equals(me.$inner)){
				me._product[10]=(String)args[3];
			}
			return true;
		}		
	};	
	private static final callback $PNetIncludingTaxesLineTotalAmount = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (null==me._product[11] && args[2].equals(me.$inner)){
				me._product[11]=(String)args[3];
			}
			return true;
		}		
	};		
	private static final callback $PCalculatedRate = new callback(){
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = (InvImportSuperXML)args[0];
			if (null==me._product[12] && args[2].equals(me.$inner)){
				me._product[12]=(String)args[3];
			}
			return true;
		}		
	};	
	
	private HashMap<String,Node> allnode = new HashMap<String,Node>();
	private Node root = new Node();
	private Node currNode = root;
	private static final String $init = "init";
	private static final String $attr = "attr";
	private static final String $body = "body";
	private static final String $inner = "inner";
	private static final String $end = "end";
	
	private void regist(String path,callback action){
		Node parent = null;
		String[] key = path.split("/");
		String[] buff = key[0].split(":");key[0] = buff[buff.length-1];
		parent = allnode.containsKey(key[0])?allnode.get(key[0]).parent:root;
		for (int c=0;c<key.length;c++){
			buff = key[c].split(":");key[c] = buff[buff.length-1];
			if (parent.child.containsKey(key[c])){
				parent = parent.child.get(key[c]);
			}else{
				Node n = new Node();
				n.nameTag = key[c];
				n.parent = parent;
				allnode.put(n.nameTag, n);
				parent.child.put(n.nameTag, n);
				parent = n;
			}
		}
		parent.action = action;
	}
	
	private class Node{
		public Node parent=null;
		public String nameTag = "";
		public HashMap<String,Node> child = new HashMap<String,Node>();
		public callback action = null;
		public int childLevel = 0;
		public StringBuffer innerText = new StringBuffer();
		public int innerBeginPoint = -1;

	};
	
	protected InputStream _is = null;
	protected TxtFileWriter _log = null;
	protected TxtFileWriter _OSR = null;
	protected TxtFileWriter _OSP = null;
	protected String _nameFile = null;
	
	public void Import(InputStream is, TxtFileWriter log, TxtFileWriter receipt, TxtFileWriter product, String nameFile) throws Exception { // strLog เป็น โฟล์เดอร์ เก็บ txt
		_is = is;_log=log;_OSR=receipt;_OSP=product;_nameFile=nameFile;
		pointer=0;
		stackXML_p=0;
		stackXML_l[0]=0;
		flagWarning = false;
		flagError = false;
		
		currNode = root;
		root.nameTag = null;
		InputStreamReader isr = null;
		try{
			isr = new InputStreamReader(_is,CharCode);
			sb.delete(0, sb.length());
			while (0<(sicb=isr.read(cb))) sb.append(cb,0,sicb);
			Object[] me={this};
			int lastchar = sb.length();
			sb.append('<');
			eof = false;
			int x;
    		while (! eof) {
    			while ('<' != sb.charAt(pointer)) pointer++;
    			if (lastchar <= pointer) { 
    				eof = true; 
    			} else { 
    				pointer++;
    				at_pointer = sb.charAt(pointer);
    				x = (at_pointer<=0xff)?at_pointer:0xff;
    				TOC_1[x].call(me); 
    			}
    		}
    		isr.close();
			isr=null;
		}catch (Exception e){
			if (null!=isr){
				isr.close();
				isr=null;
			}
			InvImportSuperXML me = this;
			me._log.setMsg(e.toString());
			System.out.println("File : "+nameFile);
			//System.out.println("me.stackXML:["+new String(me.stackXML,0,me.stackXML_p)+"]");
			throw e;
		}
    }
	
	private static final callback $TOC_1_ignor = new callback() {
    	public boolean call(Object[] args)throws Exception {
    		((InvImportSuperXML)args[0]).pointer++;
    		return true;
    	}		
	};
	
	private static final callback $TOC_1_lt = new callback() {
    	public boolean call(Object[] args)throws Exception {
    		return true;
    	}		
	};
	
	private static final callback $TOC_1_quest = new callback() {
    	public boolean call(Object[] args)throws Exception {
    		InvImportSuperXML me = ((InvImportSuperXML)args[0]);
    		int x = me.sb.length();
    		me.sb.append(">");
    		while ('>' != me.sb.charAt(me.pointer)) me.pointer++;
    		if (x <= me.pointer) {
    			me.eof = true;
    			me.eot = true;
    			return false;
    		}
    		me.pointer++;
    		me.sb.delete(x,me.sb.length());
    		me.eot = true; 
    		return true;
    	}		
	};
	
	private static final callback $TOC_1_ext = new callback() {
    	public boolean call(Object[] args)throws Exception {
    		InvImportSuperXML me = ((InvImportSuperXML)args[0]);
    		me.pointer++;
    		int x = me.sb.length();
    		
    		if ('-' == me.sb.charAt(me.pointer) && '-' == me.sb.charAt(me.pointer+1)){
        		me.sb.append(">");
        		me.eot = false;
        		while (! me.eot) {
    	    		while ('>' != me.sb.charAt(me.pointer)) me.pointer++;
    	    		if (x <= me.pointer) {
    	    			me.eof = true;
    	    			me.eot = true;
    	    			return false;
    	    		}
    	    		if ('-' == me.sb.charAt(me.pointer-2) && '-' == me.sb.charAt(me.pointer-1)) {
    	    			me.eot = true;
    	    		}
    	    		me.pointer++;
        		}
        		me.sb.delete(x,me.sb.length());       			
    		} else {
        		me.sb.append(">");
        		while ('>' != me.sb.charAt(me.pointer)) me.pointer++;
        		if (x <= me.pointer) {
        			me.eof = true;
        			me.eot = true;
        			return false;
        		}
        		me.pointer++;
        		me.sb.delete(x,me.sb.length()); 			
    		}
    		return true;
    	}		
	};

	private static final callback $TOC_1_tag = new callback() {
    	public boolean call(Object[] args)throws Exception {
    		InvImportSuperXML me = ((InvImportSuperXML)args[0]);
    		me.stackXML_l[me.stackXML_p] = me.pointer++;
    		me.stackXML[me.stackXML_p++] = '<';

    		Object[] params = {me};
    		me.eot = false;
    		char x;
    		while (! me.eot) {
    			x = me.sb.charAt(me.pointer);
    			x = (x<=0xff)?x:0xff; 
    			TOC_2[x].call(params);
    		}
    		return true;
    	}		
	};
	
	protected boolean closeTag = false;
	private static final callback $TOC_1_slash = new callback() {
    	public boolean call(Object[] args)throws Exception {
    		InvImportSuperXML me = ((InvImportSuperXML)args[0]);
    		me.stackXML_l[me.stackXML_p] = ++me.pointer;
    		me.stackXML[me.stackXML_p++] = '<';
    		me.closeTag = true;
    		Object[] params = {me};
    		me.eot = false;
    		char x='a';
    		while (! me.eot) {
    			x = me.sb.charAt(me.pointer);
    			x = (x<=0xff)?x:0xff; 
    			TOC_2[x].call(params);
    		}
    		return true;
    	}		
	};
	
	private static final callback $TOC_2_isTag = new callback() {
		public boolean call(Object[] args)throws Exception {
			((InvImportSuperXML) args[0]).pointer++;
			return true;
		}
	};

	protected static void doTag(InvImportSuperXML me) throws Exception {
		me.eot = true;
		int tagbegin = me.stackXML_l[me.stackXML_p-1];
		int tagend = me.pointer;
		String tagContent = me.sb.substring(tagbegin,tagend);
		String[] attributes = tagContent.split("~");
		String[] tagnames = attributes[0].split(":");
		String tagname = tagnames[tagnames.length-1];

		if (me.closeTag) { 

			// if end current registered tag
			if ( tagname.equals(me.currNode.nameTag) ) {
				if ( null != me.currNode.action ) {
					if (me.currNode.innerBeginPoint<= tagbegin-2) me.currNode.innerText.append(me.sb.substring(me.currNode.innerBeginPoint, tagbegin-2));
					me.currNode.innerBeginPoint = -1;

					Object[] inne={ me,tagname,me.$inner, me.currNode.innerText.toString() };
					me.currNode.action.call(inne);
					
					Object[] end={ me,tagname,me.$end };
					me.currNode.action.call(end);
				}
				
				me.currNode = me.currNode.parent;
				me.currNode.childLevel = 0;
				me.currNode.innerBeginPoint = me.pointer+1;
			} else {
				//if return to current node then start the inner text-content
				if (me.currNode.childLevel == 1) {
					me.currNode.childLevel = 0;
					me.currNode.innerBeginPoint = me.pointer+1;
				} else {
					if (me.currNode.childLevel > 1) me.currNode.childLevel--;
				}
			}			
			
		} else if (me.selfCloseTag) {
			
			if (me.currNode.child.containsKey(tagname)) {
				// if it's a registered child tag
				if ($debug) System.out.print("<"+tagname+" >");
				if (me.currNode.innerBeginPoint<= tagbegin-2) me.currNode.innerText.append(me.sb.substring(me.currNode.innerBeginPoint, tagbegin-2));
				me.currNode.innerBeginPoint = me.pointer+1;

				Node tmp = me.currNode;	
				me.currNode = me.currNode.child.get(tagname);
				if (null!=me.currNode.action){
					Object[] init={ me,me.currNode.nameTag,me.$init };
					me.currNode.action.call(init);
					
					//-- register found attributes
					for (int i = attributes.length-1; i >= 0; i-- ) {
						if (0 < attributes[i].length()) {
							Object[] att={ me,me.currNode.nameTag,me.$attr, attributes[i] };
							me.currNode.action.call(att);
						}
					}

					//-- register body
					//Object[] body={ me,me.currNode.nameTag,me.$body };
					//me.currNode.action.call(body);

					//Object[] inne={ me,tagname,me.$inner, me.currNode.innerText.toString() };
					//me.currNode.action.call(inne);
					
					Object[] end={ me,tagname,me.$end };
					me.currNode.action.call(end);
				}
				me.currNode = tmp;
			}			

		} else {
			// OpenTag
			
			//-- report found tag
			if (me.currNode.child.containsKey(tagname)) {
				// if it's a registered child tag
				if ($debug) System.out.print("<"+tagname+" >");
				me.currNode = me.currNode.child.get(tagname);
				me.currNode.childLevel = 0;
				me.currNode.innerBeginPoint = me.pointer+1;
				//me.currNode.innerText.delete(0,me.currNode.innerText.length()-1);
				me.currNode.innerText.setLength(0);
				if (null!=me.currNode.action){
					Object[] init={ me,me.currNode.nameTag,me.$init };
					me.currNode.action.call(init);
					
					//-- register found attributes
					for (int i = attributes.length-1; i >= 0; i-- ) {
						if (0 < attributes[i].length()) {
							Object[] att={ me,me.currNode.nameTag,me.$attr, attributes[i] };
							me.currNode.action.call(att);
						}
					}

					//-- register body
					Object[] body={ me,me.currNode.nameTag,me.$body };
					me.currNode.action.call(body);
				}
			} else if (null != me.root.nameTag ) {
				// if it's non-register child tag
				if (0 == me.currNode.childLevel && -1 < me.currNode.innerBeginPoint   ) {
					if (me.currNode.innerBeginPoint<= tagbegin-2) me.currNode.innerText.append(me.sb.substring(me.currNode.innerBeginPoint, tagbegin-2));
					me.currNode.innerBeginPoint = -1;
				}
				me.currNode.childLevel++;
			} else if ( tagname.contains("_CrossIndustryInvoice") ){
				// if it's a root tag
				me.root.nameTag = tagname;
				me.currNode.childLevel = 0;
				me.currNode.innerBeginPoint = me.pointer+1;
				me.currNode.innerText.delete(0,me.currNode.innerText.length());
				if (null!=me.currNode.action){
					Object[] init={ me,tagname,me.$init };
					me.root.action.call(init);
					
					//-- register found attributes
					for (int i = attributes.length-1; i >= 0; i-- ) {
						if (0 < attributes[i].length()) {
							Object[] att={ me,me.currNode.nameTag,me.$attr, attributes[i] };
							me.currNode.action.call(att);
						}
					}

					//-- register body
					Object[] body={ me,me.currNode.nameTag,me.$body };
					me.currNode.action.call(body);
				}
			}
		}
		me.closeTag = false;
		me.selfCloseTag = false;
		me.eot = true;
		if (me.pointer >= me.sb.length()-1) me.eof = true;
	}
	
	private static final callback $TOC_2_endtag = new callback() {
		public boolean call(Object[] args)throws Exception {
    		InvImportSuperXML me = ((InvImportSuperXML)args[0]);
			if ('<' == me.stackXML[me.stackXML_p-1]) {
				doTag(me);
				me.stackXML_p--;
			} else {
				me.stackXML_l[me.stackXML_p] = ++me.pointer;
				me.stackXML[me.stackXML_p++] = '>';
			}
			me.pointer++;
			return true;
		}
	}; 

	protected boolean selfCloseTag = false;
	private static final callback $TOC_2_slash = new callback() {
		public boolean call(Object[] args)throws Exception {
    		InvImportSuperXML me = ((InvImportSuperXML)args[0]);
			if ('<' == me.stackXML[me.stackXML_p-1]) {
				me.selfCloseTag = true;
				doTag(me);
				me.stackXML_p--;
			} 
			me.pointer++;
			return true;
		}
	}; 
	
	private static final callback $TOC_2_newline = new callback() {
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = ((InvImportSuperXML)args[0]);
			if ('>' == me.stackXML[me.stackXML_p-1]) {
				doTag(me);
				me.stackXML_p = me.stackXML_p-3;
			} else {
				me.sb.setCharAt(me.pointer,'~');
			}
			me.pointer++;
			return true;
		}
	}; 
	
	private static final callback $TOC_2_space = new callback() {
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = ((InvImportSuperXML)args[0]);
			if ('<' == me.stackXML[me.stackXML_p-1]) {
				me.sb.setCharAt(me.pointer,'~');
			}
			me.pointer++;
			return true;
		}
	}; 	

	private static final callback $TOC_2_Dqoute = new callback() {
		public boolean call(Object[] args)throws Exception {
    		InvImportSuperXML me = ((InvImportSuperXML)args[0]);
			Object[] params = {me};
			TOC_3[0xff & me.stackXML[me.stackXML_p-1]].call(params);
			me.pointer++;
			return true;
		}
	}; 

	private static final callback $TOC_2_Sqoute = new callback() {
		public boolean call(Object[] args)throws Exception {
    		InvImportSuperXML me = ((InvImportSuperXML)args[0]);
			Object[] params = {me};
			TOC_4[0xff & me.stackXML[me.stackXML_p-1]].call(params);
			me.pointer++;
			return true;
		}
	}; 

	private static final callback $TOC_3_lt = new callback() {
		public boolean call(Object[] args)throws Exception {
    		InvImportSuperXML me = ((InvImportSuperXML)args[0]);
			me.stackXML_l[me.stackXML_p] = me.pointer;
			me.stackXML[me.stackXML_p++] = '"';
			return true;
		}
	}; 

	private static final callback $TOC_3_Dqoute = new callback() {
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = ((InvImportSuperXML)args[0]);
    		me.stackXML_p--;
			return true;
		}
	}; 
	
	private static final callback $TOC_3_gt = new callback() {
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = ((InvImportSuperXML)args[0]);
    		me.stackXML_p -= 2;
			return true;
		}
	}; 

	private static final callback $TOC_3_Sqoute = new callback() {
		public boolean call(Object[] args)throws Exception {
			return true;
		}
	}; 

	private static final callback $TOC_4_lt = new callback() {
		public boolean call(Object[] args)throws Exception {
    		InvImportSuperXML me = ((InvImportSuperXML)args[0]);
			me.stackXML_l[me.stackXML_p] = me.pointer;
			me.stackXML[me.stackXML_p++] = '\'';
			return true;
		}
	}; 

	private static final callback $TOC_4_Dqoute = new callback() {
		public boolean call(Object[] args)throws Exception {
			return true;
		}
	}; 
	
	private static final callback $TOC_4_gt = new callback() {
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = ((InvImportSuperXML)args[0]);
    		me.stackXML_p -= 2;
			return true;
		}
	}; 

	private static final callback $TOC_4_Sqoute = new callback() {
		public boolean call(Object[] args)throws Exception {
			InvImportSuperXML me = ((InvImportSuperXML)args[0]);
    		me.stackXML_p--;
			return true;
		}
	}; 
	
//---------------------------------------------------------------------------
	
	protected interface callback{
		public boolean call(Object[] args)throws Exception ;
    }
    
	public String Clean(String val) throws Exception {
		if (val==null) return null;
		if ("".equals(val)) return "";
		return val.replaceAll("\r+", "\n").replaceAll("\n+", $newline).replaceAll("^ +| +$|( )+", "$1").replaceAll("\'", $valueSQuote).replaceAll("\"", $valueDQuote);
	}   

	public String Value(String val) throws Exception {
		if (val==null) return "";
		if ("".equals(val)) return "\"\"";
		return "\""+val+"\"";
	}   
	
	public static boolean isNumeric(String str)
	{
	  if (null == str) return true;
	  return str.replace(",","").matches("(-?\\d+(\\.\\d+)?)|(-?(\\d+)?(\\.\\d+))");  //match a number with optional '-' and decimal.
	}

	public static boolean isVatRate(String str)
	{
	  return (Float.valueOf(str)<100.00);
	}
}
