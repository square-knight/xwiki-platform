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
package org.xwiki.rest.internal.resources;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.apache.commons.lang.StringUtils;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryFilter;
import org.xwiki.rest.XWikiResource;
import org.xwiki.rest.XWikiRestException;
import org.xwiki.rest.internal.ModelFactory;
import org.xwiki.rest.internal.RangeIterable;
import org.xwiki.rest.internal.Utils;
import org.xwiki.rest.model.jaxb.Attachment;
import org.xwiki.rest.model.jaxb.Attachments;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * @version $Id$
 */
public class BaseAttachmentsResource extends XWikiResource
{
    /**
     * Helper class that contains newly created attachment information to be returned to the client. It contains the
     * JAXB attachment object and a boolean variable that states if the attachment existed before. This class is used by
     * the storeAttachment utility method.
     */
    protected static class AttachmentInfo
    {
        protected Attachment attachment;

        protected boolean alreadyExisting;

        public AttachmentInfo(Attachment attachment, boolean alreadyExisting)
        {
            this.attachment = attachment;
            this.alreadyExisting = alreadyExisting;
        }

        public Attachment getAttachment()
        {
            return attachment;
        }

        public boolean isAlreadyExisting()
        {
            return alreadyExisting;
        }
    }

    private static final Pattern COMMA = Pattern.compile("\\s*,\\s*");

    private static final Map<String, String> FILTER_TO_QUERY = new HashMap<>();

    static {
        FILTER_TO_QUERY.put("space", "doc.space");
        FILTER_TO_QUERY.put("page", "doc.fullName");
        FILTER_TO_QUERY.put("name", "attachment.filename");
        FILTER_TO_QUERY.put("author", "attachment.author");
    }

    @Inject
    private ModelFactory modelFactory;

    @Inject
    @Named("hidden/document")
    private QueryFilter hiddenDocumentFilter;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localEntityReferenceSerializer;

    /**
     * @param scope where to retrieve the attachments from; it should be a reference to a wiki, space or document
     * @param filters the filters used to restrict the set of attachments (you can filter by space name, document name,
     *            attachment name, author and mime type)
     * @param offset defines the start of the range
     * @param limit the maximum number of attachments to include in the range
     * @param withPrettyNames whether to include pretty names (like author full name and document title) in the returned
     *            attachment metadata
     * @return the list of attachments from the specified scope that match the given filters and that are within the
     *         specified range
     * @throws XWikiRestException if we fail to retrieve the attachments
     */
    protected Attachments getAttachments(EntityReference scope, Map<String, String> filters, Integer offset,
        Integer limit, Boolean withPrettyNames) throws XWikiRestException
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        String database = xcontext.getWikiId();

        Attachments attachments = objectFactory.createAttachments();

        try {
            xcontext.setWikiId(scope.extractReference(EntityType.WIKI).getName());

            List<Object> queryResult = getAttachmentsQuery(scope, filters).setLimit(limit).setOffset(offset).execute();

            Set<String> acceptedMediaTypes = getAcceptedMediaTypes(filters.getOrDefault("mediaTypes", ""));

            for (Object object : queryResult) {
                Object[] fields = (Object[]) object;
                List<String> pageSpaces = Utils.getSpacesFromSpaceId((String) fields[0]);
                String pageName = (String) fields[1];
                String pageVersion = (String) fields[2];
                XWikiAttachment xwikiAttachment = (XWikiAttachment) fields[3];

                // Not all the attachments have their media type stored in the database so we can't rely only on the
                // query-level filtering. We need to also detect the media type after the query is executed and filter
                // out the attachments that don't match the accepted media types.
                String mediaType = xwikiAttachment.getMimeType(xcontext).toUpperCase();
                boolean hasAcceptedMediaType = acceptedMediaTypes.isEmpty()
                    || acceptedMediaTypes.stream().anyMatch(acceptedMediaType -> mediaType.contains(acceptedMediaType));

                if (hasAcceptedMediaType) {
                    DocumentReference documentReference =
                        new DocumentReference(xcontext.getWikiId(), pageSpaces, pageName);
                    XWikiDocument document = new XWikiDocument(documentReference);
                    document.setVersion(pageVersion);
                    xwikiAttachment.setDoc(document, false);
                    com.xpn.xwiki.api.Attachment apiAttachment =
                        new com.xpn.xwiki.api.Attachment(new Document(document, xcontext), xwikiAttachment, xcontext);
                    attachments.getAttachments().add(this.modelFactory.toRestAttachment(this.uriInfo.getBaseUri(),
                        apiAttachment, withPrettyNames, false));
                }
            }
        } catch (QueryException e) {
            throw new XWikiRestException(e);
        } finally {
            xcontext.setWikiId(database);
        }

        return attachments;
    }

    private Query getAttachmentsQuery(EntityReference scope, Map<String, String> filters) throws QueryException
    {
        StringBuilder statement = new StringBuilder().append("select doc.space, doc.name, doc.version, attachment")
            .append(" from XWikiDocument as doc, XWikiAttachment as attachment");

        Map<String, String> exactParams = new HashMap<>();
        Map<String, String> prefixParams = new HashMap<>();
        Map<String, String> containsParams = new HashMap<>();

        List<String> whereClause = new ArrayList<>();
        whereClause.add("attachment.docId = doc.id");

        // Apply the specified scope.
        if (scope.getType() == EntityType.DOCUMENT) {
            whereClause.add("doc.fullName = :localDocumentReference");
            exactParams.put("localDocumentReference", this.localEntityReferenceSerializer.serialize(scope));
        } else if (scope.getType() == EntityType.SPACE) {
            whereClause.add("(doc.space = :localSpaceReference or doc.space like :localSpaceReferencePrefix)");
            String localSpaceReference = this.localEntityReferenceSerializer.serialize(scope);
            exactParams.put("localSpaceReference", localSpaceReference);
            prefixParams.put("localSpaceReferencePrefix", localSpaceReference + '.');
        }

        // Apply the specified filters.
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            String column = FILTER_TO_QUERY.get(entry.getKey());
            if (!StringUtils.isEmpty(entry.getValue()) && column != null) {
                whereClause.add(String.format("upper(%s) like :%s", column, entry.getKey()));
                containsParams.put(entry.getKey(), entry.getValue().toUpperCase());
            }
        }
        Set<String> acceptedMediaTypes = getAcceptedMediaTypes(filters.getOrDefault("mediaTypes", ""));
        if (!acceptedMediaTypes.isEmpty()) {
            List<String> mediaTypeConstraints = new ArrayList<>();
            // Not all the attachments have their media type saved in the database. We will filter out these attachments
            // afterwards.
            mediaTypeConstraints.add("attachment.mimeType is null");
            mediaTypeConstraints.add("attachment.mimeType = ''");
            int index = 0;
            for (String mediaType : acceptedMediaTypes) {
                String parameterName = "mediaType" + index++;
                mediaTypeConstraints.add("upper(attachment.mimeType) like :" + parameterName);
                containsParams.put(parameterName, mediaType);
            }
            whereClause.add("(" + StringUtils.join(mediaTypeConstraints, " or ") + ")");
        }

        statement.append(" where ").append(StringUtils.join(whereClause, " and "));

        Query query = queryManager.createQuery(statement.toString(), Query.XWQL);

        // Bind the query parameter values.
        for (Map.Entry<String, String> entry : exactParams.entrySet()) {
            query.bindValue(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : prefixParams.entrySet()) {
            query.bindValue(entry.getKey()).literal(entry.getValue()).anyChars();
        }
        for (Map.Entry<String, String> entry : containsParams.entrySet()) {
            query.bindValue(entry.getKey()).anyChars().literal(entry.getValue()).anyChars();
        }

        query.addFilter(this.hiddenDocumentFilter);

        return query;
    }

    private Set<String> getAcceptedMediaTypes(String mediaTypesFilter)
    {
        return Arrays.asList(COMMA.split(mediaTypesFilter)).stream().filter(s -> !s.isEmpty()).map(String::toUpperCase)
            .collect(Collectors.toSet());
    }

    protected Attachments getAttachmentsForDocument(Document doc, int start, int number, Boolean withPrettyNames)
    {
        Attachments attachments = this.objectFactory.createAttachments();

        RangeIterable<com.xpn.xwiki.api.Attachment> attachmentsRange =
            new RangeIterable<com.xpn.xwiki.api.Attachment>(doc.getAttachmentList(), start, number);
        for (com.xpn.xwiki.api.Attachment xwikiAttachment : attachmentsRange) {
            attachments.getAttachments().add(
                this.modelFactory.toRestAttachment(this.uriInfo.getBaseUri(), xwikiAttachment, withPrettyNames, false));
        }

        return attachments;
    }

    protected AttachmentInfo storeAttachment(Document doc, String attachmentName, byte[] content) throws XWikiException
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        XWiki xwiki = xcontext.getWiki();

        XWikiDocument xwikiDocument = xwiki.getDocument(doc.getDocumentReference(), xcontext);

        boolean alreadyExisting = xwikiDocument.getAttachment(attachmentName) != null;

        XWikiAttachment xwikiAttachment;
        try {
            xwikiAttachment = xwikiDocument.setAttachment(attachmentName,
                new ByteArrayInputStream(content != null ? content : new byte[0]), xcontext);
        } catch (IOException e) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_STORE, XWikiException.ERROR_XWIKI_STORE_MISC,
                String.format("Failed to store the content of attachment [%s] in document [%s].", attachmentName,
                    doc.getPrefixedFullName()),
                e);
        }

        xwiki.saveDocument(xwikiDocument, xcontext);

        URL url = xcontext.getURLFactory().createAttachmentURL(attachmentName, doc.getSpace(),
            doc.getDocumentReference().getName(), "download", null, doc.getWiki(), xcontext);
        String attachmentXWikiAbsoluteUrl = url.toString();
        String attachmentXWikiRelativeUrl = xcontext.getURLFactory().getURL(url, xcontext);

        Attachment attachment = this.modelFactory.toRestAttachment(uriInfo.getBaseUri(),
            new com.xpn.xwiki.api.Attachment(doc, xwikiAttachment, xcontext), attachmentXWikiRelativeUrl,
            attachmentXWikiAbsoluteUrl, false, false);

        return new AttachmentInfo(attachment, alreadyExisting);
    }
}
