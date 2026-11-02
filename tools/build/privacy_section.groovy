// BEGIN included '_uas_privacy.groovy'
// Privacy option (UAS_MASK_PRIVATE_DATA at the top of the script). When it is true, names and addresses that
// identify the organization are replaced in the output with stable placeholders (site-1, url-1, instance-1,
// host-1, db-1, email-1, path-1, ...). Counts, versions and technical details are kept, so the report can
// still be assessed. Read-only; works on Liferay 6.1 and later.

def uasInitPrivacy(boolean enabled) {
    UASUtils.maskEnabled = enabled
    if (!enabled) {
        return
    }
    def load = { String className ->
        try {
            return Class.forName(className, true, this.getClass().getClassLoader())
        } catch (Throwable ignored) {
            return null
        }
    }
    def propsUtil = load('com.liferay.portal.kernel.util.PropsUtil')
    def restrictions = load('com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil')

    // Virtual instances
    try {
        def companyService = load('com.liferay.portal.kernel.service.CompanyLocalServiceUtil') ?: load('com.liferay.portal.service.CompanyLocalServiceUtil')
        companyService.getCompanies().each { company ->
            UASUtils.addMaskToken('instance', company.webId, company.companyId)
            try { UASUtils.addMaskToken('instance', company.name, company.companyId) } catch (Throwable ignored) {}
            try { UASUtils.addMaskToken('host', company.virtualHostname) } catch (Throwable ignored) {}
            try { UASUtils.addMaskToken('host', company.mx) } catch (Throwable ignored) {}
        }
    } catch (Throwable ignored) {
    }

    // Sites: names (all languages) and friendly URLs
    try {
        def groupService = load('com.liferay.portal.kernel.service.GroupLocalServiceUtil') ?: load('com.liferay.portal.service.GroupLocalServiceUtil')
        def query = groupService.dynamicQuery()
        query.add(restrictions.eq('site', true))
        groupService.dynamicQuery(query).each { group ->
            try { group.getNameMap().values().each { UASUtils.addMaskToken('site', it, group.groupId) } } catch (Throwable ignored) {}
            try { UASUtils.addMaskToken('site', group.nameCurrentValue, group.groupId) } catch (Throwable ignored) {}
            try { UASUtils.addMaskToken('site', group.getName(), group.groupId) } catch (Throwable ignored) {}
            try { UASUtils.addMaskToken('site', group.getDescriptiveName(), group.groupId) } catch (Throwable ignored) {}
            try { UASUtils.addMaskToken('url', group.friendlyURL, group.groupId) } catch (Throwable ignored) {}
        }
    } catch (Throwable ignored) {
    }

    // Virtual hosts (7.x)
    try {
        load('com.liferay.portal.kernel.service.VirtualHostLocalServiceUtil')?.getVirtualHosts(-1, -1)?.each {
            UASUtils.addMaskToken('host', it.hostname)
        }
    } catch (Throwable ignored) {
    }

    // Database location and installation paths
    try {
        def jdbcUrl = propsUtil?.get('jdbc.default.url')
        def hostMatcher = (jdbcUrl ?: '') =~ /\/\/([^\/:;?]+)/
        if (hostMatcher.find()) {
            UASUtils.addMaskToken('host', hostMatcher.group(1))
        }
        def dbMatcher = (jdbcUrl ?: '') =~ /\/([A-Za-z0-9_\-]+)(?:\?|;|$)/
        if (dbMatcher.find()) {
            UASUtils.addMaskToken('db', dbMatcher.group(1))
        }
        UASUtils.addMaskToken('db', propsUtil?.get('jdbc.default.username'))
        UASUtils.addMaskToken('db', propsUtil?.get('jdbc.default.jndi.name'))
    } catch (Throwable ignored) {
    }
    try {
        [propsUtil?.get('liferay.home'), System.getProperty('catalina.home'), System.getProperty('catalina.base')].each {
            UASUtils.addMaskToken('path', it)
        }
    } catch (Throwable ignored) {
    }
    UASUtils.compileMaskTokens()
}

// END included '_uas_privacy.groovy'

