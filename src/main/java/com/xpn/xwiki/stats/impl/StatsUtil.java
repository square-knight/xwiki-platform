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
 *
 */

package com.xpn.xwiki.stats.impl;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.portals.graffito.jcr.query.Filter;
import org.apache.portals.graffito.jcr.query.QueryManager;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.store.XWikiHibernateStore;
import com.xpn.xwiki.store.jcr.XWikiJcrStore;
import com.xpn.xwiki.util.Util;
import com.xpn.xwiki.web.XWikiRequest;

/**
 * Utility class for statistics.
 * 
 * @version $Id: $
 */
public final class StatsUtil
{
    /**
     * Logging tools.
     */
    private static final Log LOG = LogFactory.getLog(StatsUtil.class);

    /**
     * The name of the property in XWiki configuration file containing the list of cookie domains.
     */
    private static final String CFGPROP_COOKIEDOMAINS = "xwiki.authentication.cookiedomains";

    /**
     * The name of the property in XWiki configuration file indicating if statistics are enabled.
     */
    private static final String CFGPROP_STATS = "xwiki.stats";

    /**
     * The name of the property in XWiki configuration file indicating if a virtual wiki store statistics by default.
     */
    private static final String CFGPROP_STATS_DEFAULT = "xwiki.stats.default";

    /**
     * The prefix name of the session property containing recent statistics actions.
     */
    private static final String SESSPROP_RECENT_PREFFIX = "recent_";

    /**
     * The prefix name of the session property containing the current visit object.
     */
    private static final String SESSPROP_VISITOBJECT = "visitObject";

    /**
     * The name of the session property containing the size of the recent list of visit statistics actions.
     */
    private static final String PREFPROP_RECENT_VISITS_SIZE = "recent_visits_size";

    /**
     * The name of the XWiki preferences property indicating if current wiki store statistics.
     */
    private static final String PREFPROP_STATISTICS = "statistics";

    /**
     * The name of the request property containing the referer.
     */
    private static final String REQPROP_REFERER = "referer";

    /**
     * The name of the request property containing the user agent.
     */
    private static final String REQPROP_USERAGENT = "User-Agent";

    /**
     * The name of the context property containing the statistics cookie name.
     */
    private static final String CONTPROP_STATS_COOKIE = "stats_cookie";

    /**
     * The name of the context property indicating if the cookie in the context is new.
     */
    private static final String CONTPROP_STATS_NEWCOOKIE = "stats_newcookie";

    /**
     * The name of the cookie property containing the unique id of the visit object.
     */
    private static final String COOKPROP_VISITID = "visitid";

    /**
     * The full name of the guest virtual user.
     */
    private static final String GUEST_FULLNAME = "XWiki.XWikiGuest";

    /**
     * The list of cookie domains.
     */
    private static String[] cookieDomains;

    /**
     * The expiration date of the cookie.
     */
    private static Date cookieExpirationDate;

    /**
     * The type of the period.
     * 
     * @version $Id: $
     * @since 1.4M1
     */
    public enum PeriodType
    {
        /**
         * Based on month.
         */
        MONTH,
        /**
         * Based on day.
         */
        DAY
    }

    /**
     * Default {@link StatsUtil} constructor.
     */
    private StatsUtil()
    {
    }

    /**
     * Computes an integer representation of the passed date using the following format:
     * <ul>
     * <li>"yyyMMdd" for {@link PeriodType#DAY}</li>
     * <li>"yyyMM" for {@link PeriodType#MONTH}</li>
     * </ul>.
     * 
     * @param date the date for which to return an integer representation.
     * @param type the date type. It can be {@link PeriodType#DAY} or {@link PeriodType#MONTH}.
     * @return the integer representation of the specified date.
     * @see java.text.SimpleDateFormat
     * @since 1.4M1
     */
    public static int getPeriodAsInt(Date date, PeriodType type)
    {
        int period;

        Calendar cal = Calendar.getInstance();
        if (date != null) {
            cal.setTime(date);
        }

        if (type == PeriodType.MONTH) {
            // The first month of the year is JANUARY which is 0
            period = cal.get(Calendar.YEAR) * 100 + (cal.get(Calendar.MONTH) + 1);
        } else {
            // The first day of the month has value 1
            period =
                cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH);
        }

        return period;
    }

    /**
     * @param context the XWiki context.
     * @return the list of cookie domains.
     * @since 1.4M1
     */
    public static String[] getCookieDomains(XWikiContext context)
    {
        if (cookieDomains == null) {
            cookieDomains = StringUtils.split(context.getWiki().Param(CFGPROP_COOKIEDOMAINS), ",");
        }

        return cookieDomains;
    }

    /**
     * @return the expiration date of the cookie.
     * @since 1.4M1
     */
    public static Date getCookieExpirationDate()
    {
        // Let's init the expirationDate for the cookie
        Calendar cal = Calendar.getInstance();
        cal.set(2030, 0, 0);
        cookieExpirationDate = cal.getTime();

        return cookieExpirationDate;
    }

    /**
     * @param context the XWiki context from where to get the HTTP session session.
     * @param action the action id.
     * @return the recent statistics actions stored in the session.
     * @since 1.4M1
     */
    public static Collection< ? > getRecentActionFromSessions(XWikiContext context, String action)
    {
        return (Collection< ? >) context.getRequest().getSession().getAttribute(SESSPROP_RECENT_PREFFIX + action);
    }

    /**
     * Store the recent statistics actions in the session.
     * 
     * @param context the XWiki context from where to get the HTTP session session.
     * @param action the action id.
     * @param actions the actions.
     * @since 1.4M1
     */
    public static void setRecentActionsFromSession(XWikiContext context, String action, Collection< ? > actions)
    {
        context.getRequest().getSession().setAttribute(SESSPROP_RECENT_PREFFIX + action, actions);
    }

    /**
     * @param context the XWiki context.
     * @return the size of the recent list of visit statistics actions.
     * @since 1.4M1
     */
    public static int getRecentVisitSize(XWikiContext context)
    {
        return context.getWiki().getXWikiPreferenceAsInt(PREFPROP_RECENT_VISITS_SIZE, 20, context);
    }

    /**
     * @param session the session.
     * @return the visit object stored in the session.
     * @since 1.4M1
     */
    public static VisitStats getVisitFromSession(HttpSession session)
    {
        return (VisitStats) session.getAttribute(SESSPROP_VISITOBJECT);
    }

    /**
     * Store the visit object in the session.
     * 
     * @param session the session.
     * @param visitStat the visit object.
     * @since 1.4M1
     */
    public static void setVisitInSession(HttpSession session, VisitStats visitStat)
    {
        session.setAttribute(SESSPROP_VISITOBJECT, visitStat);
    }

    /**
     * @param context the XWiki context.
     * @return true if statistics are enabled, false otherwise.
     * @since 1.4M1
     */
    public static boolean isStatsEnabled(XWikiContext context)
    {
        return "1".equals(context.getWiki().Param(CFGPROP_STATS, "1"));
    }

    /**
     * @param context the XWiki context
     * @return true if statistics are enabled for this wiki, false otherwise.
     * @since 1.4M1
     */
    public static boolean isWikiStatsEnabled(XWikiContext context)
    {
        String statsdefault = context.getWiki().Param(CFGPROP_STATS_DEFAULT);
        String statsactive = context.getWiki().getXWikiPreference(PREFPROP_STATISTICS, "", context);

        return "1".equals(statsactive) || (("".equals(statsactive)) && ("1".equals(statsdefault)));
    }

    /**
     * Try to find the visiting session of the current request, or create a new one if this request is not part of a
     * visit. The session is searched in the following way:
     * <ol>
     * <li>the java session is searched for the visit object</li>
     * <li>try to find the stored session using the cookie</li>
     * <li>try to find the session by matching the IP and User Agent</li>
     * </ol>
     * The session is invalidated if:
     * <ul>
     * <li>the cookie is not the same as the stored cookie</li>
     * <li>more than 30 minutes have elapsed from the previous request</li>
     * <li>the user is not the same</li>
     * </ul>
     * 
     * @param context The context of this request.
     * @return The visiting session, retrieved from the database or created.
     * @since 1.4M1
     */
    public static VisitStats findVisit(XWikiContext context)
    {
        XWikiRequest request = context.getRequest();
        HttpSession session = request.getSession(true);

        VisitStats visitObject = StatsUtil.getVisitFromSession(session);

        Cookie cookie = (Cookie) context.get(CONTPROP_STATS_COOKIE);
        boolean newcookie = ((Boolean) context.get(CONTPROP_STATS_NEWCOOKIE)).booleanValue();

        if (visitObject == null) {
            visitObject = findVisitByCookieOrIPUA(context);
        }

        if (visitObject == null || !isVisitObjectValid(visitObject, context)) {
            visitObject = createNewVisit(context);
        } else {
            if (!newcookie) {
                // If the cookie is not yet the unique ID we need to change that
                String uniqueID = visitObject.getUniqueID();
                String oldcookie = visitObject.getCookie();

                if (!uniqueID.equals(oldcookie)) {
                    // We need to store the oldID so that we can remove the older entry
                    // since the entry identifiers are changing
                    VisitStats newVisitObject = (VisitStats) visitObject.clone();
                    newVisitObject.rememberOldObject(visitObject);
                    newVisitObject.setUniqueID(cookie.getValue());
                    visitObject = newVisitObject;
                }
            }

            if ((!context.getUser().equals(GUEST_FULLNAME)) && (visitObject.getUser().equals(GUEST_FULLNAME))) {
                // The user has changed from guest to an authenticated user
                // We want to record this
                VisitStats newVisitObject = visitObject;
                newVisitObject.rememberOldObject(visitObject);
                newVisitObject.setName(context.getUser());
                visitObject = newVisitObject;
            }
        }

        // Keep the visit object in the session
        StatsUtil.setVisitInSession(session, visitObject);

        return visitObject;
    }

    /**
     * Try to find the visit object in the database from cookie if it's not a new cookie, search by unique id otherwise.
     * 
     * @param context the XWiki context.
     * @return the visit statistics object found.
     * @since 1.4M1
     */
    private static VisitStats findVisitByCookieOrIPUA(XWikiContext context)
    {
        VisitStats visitStats = null;

        XWikiRequest request = context.getRequest();

        Cookie cookie = (Cookie) context.get(CONTPROP_STATS_COOKIE);
        boolean newcookie = ((Boolean) context.get(CONTPROP_STATS_NEWCOOKIE)).booleanValue();

        if (!newcookie) {
            try {
                visitStats = findVisitByCookie(cookie.getValue(), context);
            } catch (XWikiException e) {
                LOG.error("Failed to find visit by cookie", e);
            }
        } else {
            try {
                String ip = request.getRemoteAddr();
                String ua = request.getHeader(REQPROP_USERAGENT);
                visitStats = findVisitByIPUA(ip + ua, context);
            } catch (XWikiException e) {
                LOG.error("Failed to find visit by unique id", e);
            }
        }

        return visitStats;
    }

    /**
     * Indicate of the provided visit object has to be recreated.
     * 
     * @param visitObject the visit object to validate.
     * @param context the XWiki context.
     * @return false if the visit object has to be recreated, true otherwise.
     * @since 1.4M1
     */
    private static boolean isVisitObjectValid(VisitStats visitObject, XWikiContext context)
    {
        boolean valid = true;

        XWikiRequest request = context.getRequest();
        HttpSession session = request.getSession(true);
        Cookie cookie = (Cookie) context.get(CONTPROP_STATS_COOKIE);
        Date nowDate = new Date();

        if (visitObject != null) {
            // Let's verify if the session is valid
            // If the cookie is not the same
            if (!visitObject.getCookie().equals(cookie.getValue())) {
                // Let's log a message here
                // Since the session is also maintained using a cookie
                // then there is something wrong here
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found visit with cookie " + visitObject.getCookie() + " in session " + session.getId()
                        + " for request with cookie " + cookie.getValue());
                }

                valid = false;
            } else if ((nowDate.getTime() - visitObject.getEndDate().getTime()) > 30 * 60 * 1000) {
                // If session is longer than 30 minutes we should invalidate it
                // and create a new one
                valid = false;
            } else if (visitObject != null && !context.getUser().equals(visitObject.getName())) {
                // If the user is not the same, we should invalidate the session
                // and create a new one
                valid = false;
            }
        }

        return valid;
    }

    /**
     * Create and initialize a new visit statistics object.
     * 
     * @param context the XWiki context.
     * @return the new visit statistics object.
     * @since 1.4M1
     */
    private static VisitStats createNewVisit(XWikiContext context)
    {
        VisitStats visitStats = null;

        XWikiRequest request = context.getRequest();

        Date nowDate = new Date();
        Cookie cookie = (Cookie) context.get(CONTPROP_STATS_COOKIE);
        boolean newcookie = ((Boolean) context.get(CONTPROP_STATS_NEWCOOKIE)).booleanValue();

        // we need to create the session
        String ip = request.getRemoteAddr();
        String ua = request.getHeader(REQPROP_USERAGENT);
        if (ua == null) {
            ua = "";
        }

        String uniqueID;
        if (newcookie) {
            // We cannot yet ID the user using the cookie
            // we need to use the IP and UA
            uniqueID = ip + ua;
        } else {
            // In this case we got the cookie from the request
            // so we id the user using the cookie
            uniqueID = cookie.getValue();
        }

        visitStats = new VisitStats(context.getUser(), uniqueID, cookie.getValue(), ip, ua, nowDate, PeriodType.MONTH);
        visitStats.setEndDate(nowDate);

        return visitStats;
    }

    /**
     * Search visit statistics object in the database based on cookie name.
     * 
     * @param fieldName the field name.
     * @param fieldValue the field value.
     * @param context the XWiki context.
     * @return the visit object, null if no object was found.
     * @throws XWikiException error when searching for visit object.
     * @since 1.4M1
     */
    protected static VisitStats findVisitByField(String fieldName, String fieldValue, XWikiContext context)
        throws XWikiException
    {
        VisitStats visitStats = null;

        Date currentDate = new Date(new Date().getTime() - 30 * 60 * 1000);

        if (context.getWiki().getNotCacheStore() instanceof XWikiJcrStore) {
            XWikiJcrStore store = (XWikiJcrStore) context.getWiki().getNotCacheStore();

            try {
                QueryManager qm = store.getObjectQueryManager(context);
                Filter filter =
                    qm.createFilter(VisitStats.class).addEqualTo(fieldName, fieldValue).addGreaterThan(
                        VisitStats.Property.endDate.toString(), currentDate);
                org.apache.portals.graffito.jcr.query.Query query = qm.createQuery(filter);
                query.addOrderByDescending(VisitStats.Property.endDate.toString());
                List< ? > solist = store.getObjects(query, context);
                if (solist.size() > 0) {
                    visitStats = (VisitStats) solist.get(0);
                }
            } catch (Exception e) {
                LOG.error("Failed to search visit object in the jcr store from cookie name", e);
            }
        } else {
            XWikiHibernateStore store = context.getWiki().getHibernateStore();

            try {
                List<Object> paramList = new ArrayList<Object>(2);

                String query =
                    "from VisitStats as obj where obj." + fieldName + "=? and obj.endDate > ?"
                        + " order by obj.endDate desc";

                paramList.add(fieldValue);
                paramList.add(currentDate);

                List< ? > solist = store.search(query, 0, 0, paramList, context);

                if (solist.size() > 0) {
                    visitStats = (VisitStats) solist.get(0);
                }
            } catch (Exception e) {
                LOG.error("Failed to search visit object in the database from " + fieldName, e);
            }
        }

        return visitStats;
    }

    /**
     * Search visit statistics object in the database based on cookie name.
     * 
     * @param cookie the cookie name.
     * @param context the XWiki context.
     * @return the visit object, null if no object was found.
     * @throws XWikiException error when searching for visit object.
     * @since 1.4M1
     */
    protected static VisitStats findVisitByCookie(String cookie, XWikiContext context) throws XWikiException
    {
        return findVisitByField("cookie", cookie, context);
    }

    /**
     * Search visit statistics object in the database based on visit unique id.
     * 
     * @param uniqueID the visit unique id.
     * @param context the XWiki context.
     * @return the visit object.
     * @throws XWikiException error when searching for visit object.
     * @since 1.4M1
     */
    protected static VisitStats findVisitByIPUA(String uniqueID, XWikiContext context) throws XWikiException
    {
        return findVisitByField("uniqueID", uniqueID, context);
    }

    /**
     * Create a new visit cookie and return it.
     * 
     * @param context the XWiki context.
     * @return the newly created cookie.
     * @since 1.4M1
     */
    protected static Cookie addCookie(XWikiContext context)
    {
        Cookie cookie = new Cookie(COOKPROP_VISITID, RandomStringUtils.randomAlphanumeric(32).toUpperCase());
        cookie.setPath("/");

        int time = (int) (getCookieExpirationDate().getTime() - (new Date()).getTime()) / 1000;
        cookie.setMaxAge(time);

        String cookieDomain = null;
        getCookieDomains(context);
        if (cookieDomains != null) {
            String servername = context.getRequest().getServerName();
            for (int i = 0; i < cookieDomains.length; i++) {
                if (servername.indexOf(cookieDomains[i]) != -1) {
                    cookieDomain = cookieDomains[i];
                    break;
                }
            }
        }

        if (cookieDomain != null) {
            cookie.setDomain(cookieDomain);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Setting cookie " + cookie.getValue() + " for name " + cookie.getName() + " with domain "
                + cookie.getDomain() + " and path " + cookie.getPath() + " and maxage " + cookie.getMaxAge());
        }

        context.getResponse().addCookie(cookie);

        return cookie;
    }

    /**
     * Try to find the cookie of the current request or create it.
     * 
     * @param context The context of this request.
     * @return true if the cookie is created.
     * @since 1.4M1
     */
    public static boolean findCookie(XWikiContext context)
    {
        if (context.get(CONTPROP_STATS_COOKIE) != null) {
            return false;
        }

        Cookie cookie = Util.getCookie(COOKPROP_VISITID, context);
        boolean newcookie = false;

        // If the cookie does not exist we need to set it
        if (cookie == null) {
            cookie = addCookie(context);
            newcookie = true;
        }

        context.put(CONTPROP_STATS_COOKIE, cookie);
        context.put(CONTPROP_STATS_NEWCOOKIE, Boolean.valueOf(newcookie));

        return true;
    }

    /**
     * @param context the XWiki context.
     * @return the referer.
     * @since 1.4M1
     */
    public static String getReferer(XWikiContext context)
    {
        String referer = context.getRequest().getHeader(REQPROP_REFERER);

        try {
            URL url = new URL(referer);
            URL baseurl = context.getURL();
            if (baseurl.getHost().equals(url.getHost())) {
                referer = null;
            }
        } catch (MalformedURLException e) {
            referer = null;
        }

        return referer;
    }
}
