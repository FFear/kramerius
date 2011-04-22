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
package cz.incad.kramerius.fedora.impl;

import static cz.incad.kramerius.fedora.impl.TestDataPrepare.drobnustkyRelsExt;
import static cz.incad.kramerius.fedora.impl.TestDataPrepare.drobnustkyWithIMGFULL;
import static cz.incad.kramerius.fedora.impl.TestDataPrepare.drobnustkyWithOutIMGFULL;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static org.easymock.EasyMock.createMockBuilder;
import static org.easymock.EasyMock.replay;

import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.Test;
import org.xml.sax.SAXException;

import cz.incad.kramerius.FedoraAccess;
import cz.incad.kramerius.impl.FedoraAccessImpl;
import cz.incad.kramerius.utils.conf.KConfiguration;

public class FedoraAccessImplTest {
    
    /** Test getModelName method */
    @Test
    public void testGetKrameriusModelName() throws IOException, ParserConfigurationException, SAXException {
        FedoraAccess fa = createMockBuilder(FedoraAccessImpl.class)
        .withConstructor(KConfiguration.getInstance())
        .addMockedMethod("getRelsExt")
        .createMock();
        
        drobnustkyRelsExt(fa);
        
        replay(fa);

        String monographModel = fa.getKrameriusModelName(fa.getRelsExt("0eaa6730-9068-11dd-97de-000d606f5dc6"));
        assertEquals("monograph", monographModel);
        for (String page : TestDataPrepare.DROBNUSTKY_UUIDS) {
            if (!page.equals("0eaa6730-9068-11dd-97de-000d606f5dc6")) {
                String pageModel = fa.getKrameriusModelName(fa.getRelsExt(page));
                assertEquals("page", pageModel);
            }
        }
    }

    /** Test getDonator method */
    @Test
    public void testGetDonator() throws IOException, ParserConfigurationException, SAXException {
        FedoraAccess fa = createMockBuilder(FedoraAccessImpl.class)
        .withConstructor(KConfiguration.getInstance())
        .addMockedMethod("getRelsExt")
        .createMock();
        
        drobnustkyRelsExt(fa);
        
        replay(fa);

        String donator = fa.getDonator(fa.getRelsExt("0eaa6730-9068-11dd-97de-000d606f5dc6"));
        assertEquals("norway", donator);
    }
    
    
    
    /** Test correct data - IMG_FULL present */
    @Test
    public void testFindFirstViewablePid_good() throws IOException, ParserConfigurationException, SAXException {
        // test correct data - IMG_FULL in pages
        FedoraAccess fa = createMockBuilder(FedoraAccessImpl.class)
        .withConstructor(KConfiguration.getInstance())
        .addMockedMethod("isStreamAvailable")
        .addMockedMethod("getRelsExt")
        .createMock();
        
        drobnustkyWithIMGFULL(fa);
        drobnustkyRelsExt(fa);
        
        replay(fa);
        
        String firstPageForDrobnustky = fa.findFirstViewablePid("0eaa6730-9068-11dd-97de-000d606f5dc6");
        // accept right page
        assertEquals("4308eb80-b03b-11dd-a0f6-000d606f5dc6", firstPageForDrobnustky);
    }
    
    
    
    /** Test bad data - IMG_FULL not present */
    @Test
    public void testFindFirstViewablePid_bad() throws IOException, ParserConfigurationException, SAXException {
        // test correct data - IMG_FULL in pages
        FedoraAccess fa = createMockBuilder(FedoraAccessImpl.class)
        .withConstructor(KConfiguration.getInstance())
        .addMockedMethod("isStreamAvailable")
        .addMockedMethod("getRelsExt")
        .createMock();
        
        drobnustkyWithOutIMGFULL(fa);
        drobnustkyRelsExt(fa);
        
        replay(fa);
        
        String firstPageForDrobnustky = fa.findFirstViewablePid("0eaa6730-9068-11dd-97de-000d606f5dc6");
        // nic nenalezeno.. 
        assertNull(firstPageForDrobnustky);
    }
    
    

}