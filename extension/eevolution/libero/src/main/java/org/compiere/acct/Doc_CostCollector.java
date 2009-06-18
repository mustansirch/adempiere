/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 Adempiere, Inc. All Rights Reserved.               *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * Copyright (C) 2003-2007 e-Evolution,SC. All Rights Reserved.               *
 * Contributor(s): Victor Perez www.e-evolution.com                           *
 *****************************************************************************/
package org.compiere.acct;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;

import org.adempiere.model.engines.CostDimension;
import org.adempiere.model.engines.CostEngine;
import org.compiere.model.MAccount;
import org.compiere.model.MAcctSchema;
import org.compiere.model.MCostElement;
import org.compiere.model.MDocType;
import org.compiere.model.MProduct;
import org.compiere.model.ProductCost;
import org.compiere.util.Env;
import org.eevolution.model.MPPCostCollector;
import org.eevolution.model.MPPOrderNode;

/**
 *  Post Cost Collector
 *  <pre>
 *  Table:              PP_Cost_Collector
 *  Document Types:     MOP
 *  </pre>
 *  @author victor.perez@e-evolution.com http://www.e-evolution.com
 */
public class Doc_CostCollector extends Doc
{
	/**
	 *  Constructor
	 * 	@param ass accounting schemata
	 * 	@param rs record
	 * 	@param trxName trx
	 */
	protected Doc_CostCollector (MAcctSchema[] ass, ResultSet rs, String trxName)
	{
		super(ass, MPPCostCollector.class, rs, MDocType.DOCBASETYPE_ManufacturingCostCollector, trxName);
	}   //Doc Cost Collector
	

	/**	Pseudo Line */
	private DocLine_CostCollector m_line = null;
	
	/** Collector Cost */
	private MPPCostCollector m_cc = null;
	
	/**
	 *  Load Document Details
	 *  @return error message or null
	 */
	protected String loadDocumentDetails()
	{
		setC_Currency_ID (NO_CURRENCY);
		m_cc = (MPPCostCollector)getPO();
		setDateDoc (m_cc.getMovementDate());
		setDateAcct(m_cc.getMovementDate());
		
		//	Pseudo Line
		m_line = new DocLine_CostCollector (m_cc, this); 
		m_line.setQty (m_cc.getMovementQty(), false);    //  sets Trx and Storage Qty
		
		//	Pseudo Line Check
		if (m_line.getM_Product_ID() == 0)
			log.warning(m_line.toString() + " - No Product");
		log.fine(m_line.toString());
		return null;
	}   //  loadDocumentDetails

	/**
	 *  Get Balance
	 *  @return Zero (always balanced)
	 */
	public BigDecimal getBalance()
	{
		BigDecimal retValue = Env.ZERO;
		return retValue;
	}   //  getBalance

	/**
	 *  Create Facts (the accounting logic) for
	 *  @param as accounting schema
	 *  @return Fact
	 */
	public ArrayList<Fact> createFacts (MAcctSchema as)
	{
		//TODO: I need write the rule for calculate variances
		
		//  create Fact Header
		Fact fact = new Fact(this, as, Fact.POST_Actual);
		setC_Currency_ID (as.getC_Currency_ID());
		final MProduct product = MProduct.get(getCtx(), m_cc.getM_Product_ID());
		final String costingMethod = MCostElement.COSTINGMETHOD_StandardCosting;
		final Collection<MCostElement> elements = MCostElement.getByCostingMethod(getCtx(), costingMethod);

		/**
		 * The Receipt Finish good product created the next account fact
		 * Debit Product Asset Account for each Cost Element using Current Cost 
		 * Create a fact line for each cost element type
		 * 		Material
		 * 		Labor(Resources)
		 * 		Burden
		 * 		Overhead 
		 * 		Outsite Processing
		 * Debit Scrap Account for each Cost Element using Current Cost 
		 * Create a fact line for each cost element type
		 * 		Material
		 * 		Labor(Resources)
		 * 		Burden
		 * 		Overhead 
		 * 		Outsite Processing			
		 * Credit Work in Process Account for each Cost Element using Current Cost 
		 * Create a fact line for each cost element type
		 * 		Material
		 * 		Labor(Resources)
		 * 		Burden
		 * 		Overhead 
		 * 		Outsite Processing		
		 */
		if(MPPCostCollector.COSTCOLLECTORTYPE_MaterialReceipt.equals(m_cc.getCostCollectorType()))
		{
			//Finish good
			final MAccount credit = m_line.getAccount(ProductCost.ACCTTYPE_P_WorkInProcess, as);
			
			for(MCostElement element : elements)
			{
				if (m_cc.getMovementQty().signum() != 0)
				{
					MAccount debit = m_line.getAccount(ProductCost.ACCTTYPE_P_Asset, as);
					BigDecimal cost = CostEngine.getCostsByElement(product, as,
					m_cc.getAD_Org_ID(), m_cc.getM_AttributeSetInstance_ID(), 
					as.getM_CostType_ID() , element.get_ID(), m_cc.getMovementQty());

					createLines(element, as, fact, product, debit, credit, cost, m_cc.getMovementQty());
				}
				if(m_cc.getScrappedQty().signum() != 0)
				{
					MAccount debit = m_line.getAccount(ProductCost.ACCTTYPE_P_Scrap, as);
					BigDecimal cost = CostEngine.getCostsByElement(product, as,
							m_cc.getAD_Org_ID(), m_cc.getM_AttributeSetInstance_ID(), 
							as.getM_CostType_ID() , element.get_ID(), m_cc.getScrappedQty());
					createLines(element, as, fact, product, debit, credit, cost, m_cc.getScrappedQty());
				}
			}
		}
		/**
		 * The Issue Component product created next account fact
		 * Debit Work in Process Account for each Cost Element using current cost
		 * Create a fact line for each cost element type
		 * 		Material
		 * 		Labor(Resources)
		 * 		Burden
		 * 		Overhead 
		 * 		Outsite Processing	
		 * Credit Product Asset Account for each Cost Element using current cost
		 * Create a fact line for each cost element type
		 * 		Material
		 * 		Labor(Resources)
		 * 		Burden
		 * 		Overhead 
		 * 		Outsite Processing		
		 * Credit Floor Stock Account for each Cost Element using current cost
		 * Create a fact line for each cost element type
		 * 		Material
		 * 		Labor(Resources)
		 * 		Burden
		 * 		Overhead 
		 * 		Outsite Processing		
		 */
		else if (MPPCostCollector.COSTCOLLECTORTYPE_ComponentIssue.equals(m_cc.getCostCollectorType()))
		{
			MAccount debit = m_line.getAccount(ProductCost.ACCTTYPE_P_WorkInProcess, as);
			MAccount credit = m_line.getAccount(ProductCost.ACCTTYPE_P_Asset, as);
			if(m_cc.isFloorStock())
			{
				credit = m_line.getAccount(ProductCost.ACCTTYPE_P_FloorStock, as);
			}
			for(MCostElement element : elements)
			{
				BigDecimal cost = CostEngine.getCostsByElement(product, as,
						m_cc.getAD_Org_ID(), m_cc.getM_AttributeSetInstance_ID(), 
						as.getM_CostType_ID() , element.get_ID(), m_cc.getMovementQty());
				createLines(element, as, fact, product, debit, credit, cost, m_cc.getMovementQty());
			}
		}
		/**
		 * The Method Change Variance product created next account fact
		 * Debit Work in Process Account for each Cost Element using current cost
		 * Create a fact line for each cost element type
		 * 		Material
		 * 		Labor(Resources)
		 * 		Burden
		 * 		Overhead 
		 * 		Outsite Processing	
		 * Credit Product Asset Account for each Cost Element using current cost
		 * Create a fact line for each cost element type
		 * 		Material
		 * 		Labor(Resources)
		 * 		Burden
		 * 		Overhead 
		 * 		Outsite Processing		
		 * Credit Floor Stock Account for each Cost Element using current cost
		 * Create a fact line for each cost element type
		 * 		Material
		 * 		Labor(Resources)
		 * 		Burden
		 * 		Overhead 
		 * 		Outsite Processing		
		 */
		else if (MPPCostCollector.COSTCOLLECTORTYPE_MethodChangeVariance.equals(m_cc.getCostCollectorType()))
		{
			MAccount debit = m_line.getAccount(ProductCost.ACCTTYPE_P_MethodChangeVariance, as);
			MAccount credit = m_line.getAccount(ProductCost.ACCTTYPE_P_Asset, as);
			for(MCostElement element : elements)
			{
				BigDecimal cost = CostEngine.getCostsByElement(product, as,
						m_cc.getAD_Org_ID(), m_cc.getM_AttributeSetInstance_ID(), 
						as.getM_CostType_ID() , element.get_ID(), m_cc.getMovementQty());
				createLines(element, as, fact, product, debit, credit, cost, m_cc.getMovementQty());
			}
		}
		/**
		 * Feedback Labor and Burden absorbed
		 * Debit Work in Process Account for each Labor or Burden using the current cost rate
		 * Create a fact line for labor or burden
		 * 		Labor(Resources)
		 * 		Burden
		 * Credit Labor Absorbed or Burden Absorbed Account 
		 * Create a fact line for labor or burden
		 * 		Labor Absorbed
		 * 		Burden Absorbed
		 */
		else if (MPPCostCollector.COSTCOLLECTORTYPE_ActivityControl.equals(m_cc.getCostCollectorType()))
		{
			MPPOrderNode activity = (MPPOrderNode) m_cc.getPP_Order_Node();

				MAccount debit = m_line.getAccount(ProductCost.ACCTTYPE_P_WorkInProcess, as);
				for(MCostElement element : elements)
				{
					if (!CostEngine.isActivityControlElement(element))
					{
						continue;
					}
					//
					MAccount credit = m_line.getAccount(as, element);
					CostDimension d = new CostDimension(null, as, as.getM_CostType_ID(),
							m_cc.getAD_Org_ID(), 0, element.get_ID());
					BigDecimal costs = CostEngine.getNodeCost(activity,
							m_cc.getSetupTimeReal(), m_cc.getDurationReal(),
							d, getTrxName());
					createLines(element, as, fact, product, debit, credit, costs, m_cc.getMovementQty());
				}
		}
		//
		ArrayList<Fact> facts = new ArrayList<Fact>();
		facts.add(fact);
		return facts;
	}   //  createFact
	
	private void createLines(MCostElement element, MAcctSchema as, Fact fact , MProduct product,
								MAccount debit, MAccount credit, BigDecimal cost, BigDecimal qty)
	{
		if(cost == null || debit == null || credit == null)
			return;
		
		log.info("CostElement: " +element+ "Product: "+product.getName()
					+" Debit: " + debit.getDescription() + " Credit: "+ credit.getDescription()
					+ " Cost: " + cost +" Qunatity: "+ qty);
		//  Line pointers
		FactLine dr = null;
		FactLine cr = null;
		if(cost.signum() != 0 & qty != null)
		{	
			dr = fact.createLine(m_line, debit , as.getC_Currency_ID(), cost, null);
			dr.setQty(qty);
			String desc = element.getName();
			dr.addDescription(desc);
			dr.setC_Project_ID(m_cc.getC_Project_ID());
			dr.setC_Activity_ID(m_cc.getC_Activity_ID());
			dr.setC_Campaign_ID(m_cc.getC_Campaign_ID());
			dr.setM_Locator_ID(m_cc.getM_Locator_ID());

			cr = fact.createLine(m_line, credit,as.getC_Currency_ID(), null, cost);
			cr.setQty(qty);
			cr.addDescription(desc);
			cr.setC_Project_ID(m_cc.getC_Project_ID());
			cr.setC_Activity_ID(m_cc.getC_Activity_ID());
			cr.setC_Campaign_ID(m_cc.getC_Campaign_ID());
			cr.setM_Locator_ID(m_cc.getM_Locator_ID());
		}			
	}
}   //  Doc Cost Collector
