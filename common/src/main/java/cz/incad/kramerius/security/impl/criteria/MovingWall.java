/*
 * Copyright (C) 2010 Pavel Stastny
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package cz.incad.kramerius.security.impl.criteria;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import cz.incad.kramerius.FedoraNamespaceContext;
import cz.incad.kramerius.ObjectPidsPath;
import cz.incad.kramerius.security.*;
import cz.incad.kramerius.security.impl.criteria.mw.DateLexer;
import cz.incad.kramerius.security.impl.criteria.mw.DatesParser;
import org.w3c.dom.Document;
import org.w3c.dom.Text;

import javax.xml.xpath.*;
import java.io.IOException;
import java.io.StringReader;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Level;

/**
 * 
 * Stena, ktera pousti vsechny dokumenty, ktere jsou po datumu uvedenem v konfiguraci
 * 
 * Trida vzdy porovnava pouze datum uvedem v metadatech objektu, ktery je s pravem svazan.  
 * Pokud je pravo uvedeno na objetku REPOSITORY, pak zkouma nejvyssi prvek v hierarchii 
 * 
 * (konkretni monografii, konkretni periodikum, atd..)
 */
public class MovingWall extends AbstractCriterium implements RightCriterium {

    public static String[] MODS_XPATHS={"//mods:originInfo/mods:dateIssued/text()","//mods:originInfo[@transliteration='publisher']/mods:dateIssued/text()","//mods:part/mods:date/text()"};

    
    static java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(MovingWall.class.getName());


	private XPathFactory xpfactory;
	
	public MovingWall() {
        this.xpfactory = XPathFactory.newInstance();
	}
	
    @Override
    public EvaluatingResult evalute() throws RightCriteriumException {
        int wallFromConf = Integer.parseInt((String)getObjects()[0]);
        try {
            ObjectPidsPath[] pathsToRoot = getEvaluateContext().getPathsToRoot();
            EvaluatingResult result = null;
            for (ObjectPidsPath pth : pathsToRoot) {
                String[] pids = pth.getPathFromLeafToRoot();
                for (String pid : pids) {
                    
                    if (pid.equals(SpecialObjects.REPOSITORY.getPid())) continue;
                	Document biblioMods = getEvaluateContext().getFedoraAccess().getBiblioMods(pid);
                    // try all xpaths on mods
                    for (String xp : MODS_XPATHS) {
                        result = resolveInternal(wallFromConf,pid,xp,biblioMods);
                        if (result !=null) break;
                    }
                    
                    
                    // TRUE or FALSE -> rozhodnul, nevratil NOT_APPLICABLE
                    if (result != null && (result.equals(EvaluatingResult.TRUE) ||  result.equals(EvaluatingResult.FALSE))) return result; 
                }
            }

            return result != null ? result :EvaluatingResult.NOT_APPLICABLE;
        } catch (NumberFormatException e) {
            LOGGER.log(Level.SEVERE,e.getMessage());
            return EvaluatingResult.NOT_APPLICABLE;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,e.getMessage());
            return EvaluatingResult.NOT_APPLICABLE;
        } catch (XPathExpressionException e) {
            LOGGER.log(Level.SEVERE,e.getMessage());
            return EvaluatingResult.NOT_APPLICABLE;
        }
    }

    
    
    public EvaluatingResult resolveInternal(int wallFromConf, String pid, String xpath, Document xmlDoc) throws IOException, XPathExpressionException {
        if (pid.equals(SpecialObjects.REPOSITORY.getPid())) return EvaluatingResult.NOT_APPLICABLE;
        return evaluateDoc(wallFromConf, xmlDoc, xpath);
    }



    public EvaluatingResult evaluateDoc(int wallFromConf, Document xmlDoc, String xPathExpression) throws XPathExpressionException {
        XPath xpath = xpfactory.newXPath();
        xpath.setNamespaceContext(new FedoraNamespaceContext());
        XPathExpression expr = xpath.compile(xPathExpression);
        Object date = expr.evaluate(xmlDoc, XPathConstants.NODE);
        if (date != null) {
            String patt = ((Text) date).getData();

            try {
                DatesParser dateParse = new DatesParser(new DateLexer(new StringReader(patt)));
                Date parsed = dateParse.dates();

                Calendar calFromMetadata = Calendar.getInstance();
                calFromMetadata.setTime(parsed);

                Calendar calFromConf = Calendar.getInstance();
                calFromConf.add(Calendar.YEAR, -1*wallFromConf);

                return calFromMetadata.before(calFromConf) ?  EvaluatingResult.TRUE:EvaluatingResult.FALSE;
                
            } catch (RecognitionException e) {
                LOGGER.log(Level.SEVERE,e.getMessage(),e);
                LOGGER.log(Level.SEVERE,"Returning NOT_APPLICABLE");
                return EvaluatingResult.NOT_APPLICABLE;
            } catch (TokenStreamException e) {
                LOGGER.log(Level.SEVERE,e.getMessage(),e);
                LOGGER.log(Level.SEVERE,"Returning NOT_APPLICABLE");
                return EvaluatingResult.NOT_APPLICABLE;
            }
            
        }

        
        return null;
    }


    @Override
    public RightCriteriumPriorityHint getPriorityHint() {
        return RightCriteriumPriorityHint.NORMAL;
    }

    @Override
    public boolean isParamsNecessary() {
        return true;
    }

    @Override
    public SecuredActions[] getApplicableActions() {
        return new SecuredActions[] {SecuredActions.READ};
    }

    @Override
    public boolean validateParams(Object[] vals) {
        if (vals.length == 1) {
            try {
                Integer.parseInt((String) vals[0]);
                return true;
            } catch (NumberFormatException e) {
                LOGGER.log(Level.SEVERE, e.getMessage(),e);
                return false;
            }
        } else return false;
    }
    
    
    
}
