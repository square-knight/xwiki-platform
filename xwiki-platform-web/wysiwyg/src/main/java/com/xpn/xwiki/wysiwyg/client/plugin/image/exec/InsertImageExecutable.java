/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xpn.xwiki.wysiwyg.client.plugin.image.exec;

import com.google.gwt.dom.client.Node;
import com.xpn.xwiki.wysiwyg.client.dom.Range;
import com.xpn.xwiki.wysiwyg.client.widget.rta.RichTextArea;
import com.xpn.xwiki.wysiwyg.client.widget.rta.cmd.internal.InsertHTMLExecutable;

/**
 * Handles the insertion of an image, passed through its corresponding HTML block.
 * 
 * @version $Id$
 */
public class InsertImageExecutable extends InsertHTMLExecutable
{
    /**
     * {@inheritDoc}
     * 
     * @see InsertHTMLExecutable#isExecuted(RichTextArea)
     */
    public boolean isExecuted(RichTextArea rta)
    {
        // Check if current selection perfectly wraps an image
        Range currentRange = rta.getDocument().getSelection().getRangeAt(0);
        Node startContainer = currentRange.getStartContainer();
        Node endContainer = currentRange.getEndContainer();

        if (startContainer == endContainer && (currentRange.getEndOffset() - currentRange.getStartOffset() == 1)) {
            // Check that the node inside is an image
            Node nodeInside = startContainer.getChildNodes().getItem(currentRange.getEndOffset() - 1);
            if (nodeInside.getNodeName().equalsIgnoreCase("img")) {
                return true;
            }
        }
        return false;
    }
}
