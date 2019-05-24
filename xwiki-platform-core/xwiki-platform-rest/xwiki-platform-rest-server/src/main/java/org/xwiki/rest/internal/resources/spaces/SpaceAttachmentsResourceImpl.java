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
package org.xwiki.rest.internal.resources.spaces;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Named;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.rest.XWikiRestException;
import org.xwiki.rest.internal.resources.BaseAttachmentsResource;
import org.xwiki.rest.model.jaxb.Attachments;
import org.xwiki.rest.resources.spaces.SpaceAttachmentsResource;

/**
 * @version $Id$
 */
@Component
@Named("org.xwiki.rest.internal.resources.spaces.SpaceAttachmentsResourceImpl")
public class SpaceAttachmentsResourceImpl extends BaseAttachmentsResource implements SpaceAttachmentsResource
{
    @Override
    public Attachments getAttachments(String wiki, String spaces, String name, String page, String author,
        String mediaTypes, Integer offset, Integer limit, Boolean withPrettyNames) throws XWikiRestException
    {
        Map<String, String> filters = new HashMap<>();
        filters.put("page", page);
        filters.put("name", name);
        filters.put("author", author);
        filters.put("mediaTypes", mediaTypes);

        return super.getAttachments(new SpaceReference(wiki, parseSpaceSegments(spaces)), filters, offset, limit,
            withPrettyNames);
    }
}
