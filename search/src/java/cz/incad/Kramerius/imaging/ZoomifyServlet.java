/*
 * Copyright (C) 2012 Pavel Stastny
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
/**
 * 
 */
package cz.incad.Kramerius.imaging;

import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.sql.SQLException;
import java.util.StringTokenizer;
import java.util.logging.Level;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.antlr.stringtemplate.StringTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.google.inject.Inject;

import cz.incad.Kramerius.AbstractImageServlet;
import cz.incad.kramerius.imaging.DeepZoomCacheService;
import cz.incad.kramerius.imaging.DeepZoomTileSupport;
import cz.incad.kramerius.utils.FedoraUtils;
import cz.incad.kramerius.utils.IOUtils;
import cz.incad.kramerius.utils.XMLUtils;
import cz.incad.kramerius.utils.conf.KConfiguration;
import cz.incad.kramerius.utils.imgs.KrameriusImageSupport;
import cz.incad.kramerius.utils.imgs.KrameriusImageSupport.ScalingMethod;
import cz.incad.utils.RelsExtHelper;

/**
 * @author pavels
 *
 */
public class ZoomifyServlet extends AbstractImageServlet {

    @Inject
    DeepZoomCacheService cacheService;

    @Inject
    DeepZoomTileSupport tileSupport;

    @Override
    public ScalingMethod getScalingMethod() {
        ScalingMethod method = ScalingMethod.valueOf(KConfiguration.getInstance().getProperty("deepZoom.scalingMethod", "BICUBIC_STEPPED"));
        return method;
    }

    @Override
    public boolean turnOnIterateScaling() {
        boolean highQuality = KConfiguration.getInstance().getConfiguration().getBoolean("deepZoom.iterateScaling", true);
        return highQuality;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String requestURL = req.getRequestURL().toString();
            String zoomUrl = DeepZoomServlet.disectZoom(requestURL);
            StringTokenizer tokenizer = new StringTokenizer(zoomUrl, "/");
            String pid = tokenizer.nextToken();
            String rest = tokenizer.hasMoreTokens() ?  tokenizer.nextToken() : "";
            //http://imageserver.mzk.cz/ANL/4a57a2a7-d0e9-11e1-945e-0050569d679d/ImageProperties.xml
            if (rest.equals("ImageProperties.xml")) {
                renderXMLDescriptor(pid, req, resp);
            } else {
                if (tokenizer.hasMoreTokens()) {
                    String files = tokenizer.nextToken();
                    String ext = "jpg";
                    if (files.contains(".")) {
                        ext = files.substring(files.indexOf('.')+1);
                        files = files.substring(0, files.indexOf('.'));
                    }
                    
                    StringTokenizer substokenizer = new StringTokenizer(files,"-");
                    if (substokenizer.countTokens() == 3) {
                        String level = substokenizer.nextToken();
                        String x = substokenizer.nextToken();
                        String y = substokenizer.nextToken();
                        renderTile(pid, level, x, y, ext, req, resp);
                    }
                }
            }
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }
    }

    private void renderXMLDescriptor(String pid, HttpServletRequest req, HttpServletResponse resp) throws IOException, XPathExpressionException {
        setDateHaders(pid,FedoraUtils.IMG_FULL_STREAM, resp);
        setResponseCode(pid,FedoraUtils.IMG_FULL_STREAM, req, resp);
        String relsExtUrl = RelsExtHelper.getRelsExtTilesUrl(pid, this.fedoraAccess);
        if (!relsExtUrl.equals(RelsExtHelper.CACHE_RELS_EXT_LITERAL)) {
            try {
                renderIIPrenderXMLDescriptor(pid, resp, relsExtUrl);
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } else {
            renderEmbededDZIDescriptor(pid, resp);
        
        }
    }
    
    private void renderEmbededDZIDescriptor(String uuid, HttpServletResponse resp) throws IOException, FileNotFoundException, XPathExpressionException {
        if (!cacheService.isDeepZoomDescriptionPresent(uuid)) {
            Dimension rawDim = KrameriusImageSupport.readDimension(uuid, FedoraUtils.IMG_FULL_STREAM, fedoraAccess, 0);
            int levelsOverTile = KConfiguration.getInstance().getConfiguration().getInt("deepZoom.numberStepsOverTile", 1);
            int tileLevel = tileSupport.getClosestLevel(new Dimension(rawDim.width, rawDim.height), tileSupport.getTileSize());
            Dimension scaledDimension = tileSupport.getScaledDimension(rawDim, tileLevel+levelsOverTile);
            
            cacheService.writeDeepZoomDescriptor(uuid, scaledDimension, tileSupport.getTileSize());
        }
        InputStream inputStream = cacheService.getDeepZoomDescriptorStream(uuid);
        
        //<IMAGE_PROPERTIES WIDTH="8949" HEIGHT="6684" NUMTILES="945" NUMIMAGES="1" VERSION="1.8" TILESIZE="256" />
        
        try {
            Document document = XMLUtils.parseDocument(inputStream);
            Element docelement = document.getDocumentElement();
            String tileSize = docelement.getAttribute("TileSize");
            Element sizeElement = XMLUtils.findElement(docelement, "Size");
            String width = sizeElement.getAttribute("Width");
            String height = sizeElement.getAttribute("Height");
            StringBuffer buffer = new StringBuffer();
            buffer.append("<IMAGE_PROPERTIES WIDTH=\"").append(width).append('"').append(" HEIGHT=\"").append(height).append('"');
            buffer.append("  VERSION='1.8' TILESIZE=\"").append(tileSize).append("\" />");
            //NUMTILES='945'
            
            IOUtils.copyStreams(new ByteArrayInputStream(buffer.toString().getBytes("UTF-8")), resp.getOutputStream());
        } catch (ParserConfigurationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SAXException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    
    private void renderIIPrenderXMLDescriptor(String uuid, HttpServletResponse resp, String url) throws MalformedURLException, IOException, SQLException, XPathExpressionException {
        String urlForStream = getURLForStream(uuid, url);
        if (urlForStream != null) {
            StringTemplate dziUrl = stGroup().getInstanceOf("zoomify");
            if (urlForStream.endsWith("/")) urlForStream = urlForStream.substring(0, urlForStream.length()-1);
            dziUrl.setAttribute("url", urlForStream);
            copyFromImageServer(dziUrl.toString(), resp);
        }
    }
    
    private void renderTile(String pid, String slevel, String x, String y, String ext, HttpServletRequest req, HttpServletResponse resp) throws IOException, XPathExpressionException {
        setDateHaders(pid, FedoraUtils.IMG_FULL_STREAM, resp);
        setResponseCode(pid,FedoraUtils.IMG_FULL_STREAM, req, resp);
        String relsExtUrl = RelsExtHelper.getRelsExtTilesUrl(pid, this.fedoraAccess);
        if (!relsExtUrl.equals(RelsExtHelper.CACHE_RELS_EXT_LITERAL)) {
            try {
                renderIIPTile(pid, slevel, x,y, ext, resp, relsExtUrl);
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } else {
            //renderEmbededTile(pid, slevel, stile, req, resp);
        }
    }

//    private void renderEmbededTile() {
//        String level = substokenizer.nextToken();
//        String x = substokenizer.nextToken();
//        String y = substokenizer.nextToken();
//
//    }

    private void renderIIPTile(String uuid, String slevel, String x,String y, String ext, HttpServletResponse resp, String url) throws SQLException, UnsupportedEncodingException, IOException, XPathExpressionException {
        String dataStreamUrl = getURLForStream(uuid, url);
        if (dataStreamUrl != null) {
            StringTemplate tileUrl = stGroup().getInstanceOf("zoomifytile");
            //setStringTemplateModel(uuid, dataStreamPath, tileUrl, fedoraAccess);
            if (dataStreamUrl.endsWith("/")) dataStreamUrl = dataStreamUrl.substring(0, dataStreamUrl.length()-1);
            tileUrl.setAttribute("url", dataStreamUrl);
            tileUrl.setAttribute("level", slevel);
            tileUrl.setAttribute("x", x);
            tileUrl.setAttribute("y", y);
            tileUrl.setAttribute("ext", ext);
            copyFromImageServer(tileUrl.toString(), resp);
        }
    }
}