/* METADATA
{
  name: various_search

  display_name: {
    zh: "多平台搜索"
    en: "Multi-Platform Search"
  }
  category: "Search"
  description: { zh: "提供多平台搜索功能（含图片搜索），支持从必应、百度、搜狗、夸克等平台获取搜索结果。", en: "Multi-platform search tools (including image search) that fetch results from Bing, Baidu, Sogou, Quark, and more." }
  enabledByDefault: true
  
  tools: [
    {
      name: search_bing
      description: { zh: "使用必应搜索引擎进行搜索", en: "Search using the Bing search engine." }
      parameters: [
        {
          name: query
          description: { zh: "搜索查询关键词", en: "Search query keywords." }
          type: string
          required: true
        },
        {
          name: includeLinks
          description: { zh: "是否在结果中包含可点击的链接列表，默认为false。如果为true，AI可以根据返回的链接序号进行深入访问。", en: "Whether to include a clickable link list in results (default: false). If true, the AI can follow links by index." }
          type: boolean
          required: false
        }
      ]
    },
    {
      name: search_baidu
      description: { zh: "使用百度搜索引擎进行搜索", en: "Search using the Baidu search engine." }
      parameters: [
        {
          name: query
          description: { zh: "搜索查询关键词", en: "Search query keywords." }
          type: string
          required: true
        },
        {
          name: page
          description: { zh: "搜索结果页码，默认为1", en: "Result page number (default: 1)." }
          type: string
          required: false
        },
        {
          name: includeLinks
          description: { zh: "是否在结果中包含可点击的链接列表，默认为false。如果为true，AI可以根据返回的链接序号进行深入访问。", en: "Whether to include a clickable link list in results (default: false). If true, the AI can follow links by index." }
          type: boolean
          required: false
        }
      ]
    },
    {
      name: search_sogou
      description: { zh: "使用搜狗搜索引擎进行搜索", en: "Search using the Sogou search engine." }
      parameters: [
        {
          name: query
          description: { zh: "搜索查询关键词", en: "Search query keywords." }
          type: string
          required: true
        },
        {
          name: page
          description: { zh: "搜索结果页码，默认为1", en: "Result page number (default: 1)." }
          type: string
          required: false
        },
        {
          name: includeLinks
          description: { zh: "是否在结果中包含可点击的链接列表，默认为false。如果为true，AI可以根据返回的链接序号进行深入访问。", en: "Whether to include a clickable link list in results (default: false). If true, the AI can follow links by index." }
          type: boolean
          required: false
        }
      ]
    },
    {
      name: search_quark
      description: { zh: "使用夸克搜索引擎进行搜索", en: "Search using the Quark search engine." }
      parameters: [
        {
          name: query
          description: { zh: "搜索查询关键词", en: "Search query keywords." }
          type: string
          required: true
        },
        {
          name: page
          description: { zh: "搜索结果页码，默认为1", en: "Result page number (default: 1)." }
          type: string
          required: false
        },
        {
          name: includeLinks
          description: { zh: "是否在结果中包含可点击的链接列表，默认为false。如果为true，AI可以根据返回的链接序号进行深入访问。", en: "Whether to include a clickable link list in results (default: false). If true, the AI can follow links by index." }
          type: boolean
          required: false
        }
      ]
    },
    {
      name: combined_search
      description: { zh: "在多个平台同时执行搜索。建议用户要求搜索的时候默认使用这个工具。", en: "Run searches across multiple platforms. Use this tool by default when the user asks to search." }
      parameters: [
        {
          name: query
          description: { zh: "搜索查询关键词", en: "Search query keywords." }
          type: string
          required: true
        },
        {
          name: platforms
          description: { zh: "搜索平台列表字符串，可选值包括\"bing\",\"baidu\",\"sogou\",\"quark\"，多个平台用逗号分隔，比如\"bing,baidu,sogou,quark\"", en: "Comma-separated platform list. Supported: \"bing\", \"baidu\", \"sogou\", \"quark\". Example: \"bing,baidu,sogou,quark\"." }
          type: string
          required: true
        },
        {
          name: includeLinks
          description: { zh: "是否在结果中包含可点击的链接列表，默认为false。聚合搜索时建议保持为false以节省输出，仅在需要深入访问时对单个搜索引擎使用。", en: "Whether to include a clickable link list in results (default: false). For combined search, keep it false to reduce output; enable it for a single engine when you need to open links." }
          type: boolean
          required: false
        }
      ]
    },
    {
      name: search_bing_images
      description: { zh: "使用必应图片搜索引擎进行图片搜索。返回内容会包含 visitKey 和 Images 编号；下载图片请用 download_file 的 visit_key + image_number（不要用 link_number 乱点页面链接）。", en: "Search images using Bing Images. The result includes visitKey and indexed Images; download images via download_file with visit_key + image_number (do not follow random page links via link_number)." }
      parameters: [
        {
          name: query
          description: { zh: "搜索关键词", en: "Search query keywords." }
          type: string
          required: true
        }
      ]
    },
    {
      name: search_wikimedia_images
      description: { zh: "使用 Wikimedia Commons 进行图片搜索（公共资源）。返回 visitKey + Images 编号；下载图片用 download_file(visit_key + image_number)。", en: "Search images using Wikimedia Commons (public domain/commons). Use visitKey + image_number with download_file to download images." }
      parameters: [
        {
          name: query
          description: { zh: "搜索关键词", en: "Search query keywords." }
          type: string
          required: true
        }
      ]
    },
    {
      name: search_duckduckgo_images
      description: { zh: "使用 DuckDuckGo Images 进行图片搜索。返回 visitKey + Images 编号；下载图片用 download_file(visit_key + image_number)。", en: "Search images using DuckDuckGo Images. Use visitKey + image_number with download_file to download images." }
      parameters: [
        {
          name: query
          description: { zh: "搜索关键词", en: "Search query keywords." }
          type: string
          required: true
        }
      ]
    },
    {
      name: search_ecosia_images
      description: { zh: "使用 Ecosia Images 进行图片搜索。返回 visitKey + Images 编号；下载图片用 download_file(visit_key + image_number)。", en: "Search images using Ecosia Images. Use visitKey + image_number with download_file to download images." }
      parameters: [
        {
          name: query
          description: { zh: "搜索关键词", en: "Search query keywords." }
          type: string
          required: true
        }
      ]
    },
    {
      name: search_pexels_images
      description: { zh: "使用 Pexels 进行图片搜索（高质量图库）。返回 visitKey + Images 编号；下载图片请用 download_file 的 visit_key + image_number。", en: "Search images using Pexels (high-quality stock). Use visitKey + image_number with download_file to download images." }
      parameters: [
        {
          name: query
          description: { zh: "搜索关键词", en: "Search query keywords." }
          type: string
          required: true
        }
      ]
    },
    {
      name: search_pixabay_images
      description: { zh: "使用 Pixabay 进行图片搜索（图库）。返回 visitKey + Images 编号；下载图片请用 download_file 的 visit_key + image_number。", en: "Search images using Pixabay (stock). Use visitKey + image_number with download_file to download images." }
      parameters: [
        {
          name: query
          description: { zh: "搜索关键词", en: "Search query keywords." }
          type: string
          required: true
        }
      ]
    }
  ]
}*/
const various_search = (function () {
    async function performSearch(platform, url, query, page, includeLinks = false) {
        try {
            const response = await Tools.Net.visit(url);
            if (!response) {
                throw new Error(`无法获取 ${platform} 搜索结果`);
            }
            let parts = [];
            // visitKey
            if (response.visitKey !== undefined) {
                parts.push(String(response.visitKey));
            }
            // links: [index] text （不包含链接本身）
            if (includeLinks && response.links && Array.isArray(response.links) && response.links.length > 0) {
                const linksLines = response.links.map((link, index) => `[${index + 1}] ${link.text}`);
                parts.push(linksLines.join('\n'));
            }
            // content
            if (response.content !== undefined) {
                parts.push(String(response.content));
            }
            return {
                platform,
                content: parts.join('\n')
            };
        }
        catch (error) {
            return {
                platform,
                content: `${platform} 搜索失败: ${error.message}`
            };
        }
    }
    async function performImageSearch(platform, url, query) {
        try {
            const response = await Tools.Net.visit({ url, include_image_links: true });
            if (!response) {
                throw new Error(`无法获取 ${platform} 图片搜索结果`);
            }
            let parts = [];
            if (response.visitKey !== undefined) {
                parts.push(String(response.visitKey));
            }
            if (response.imageLinks && Array.isArray(response.imageLinks) && response.imageLinks.length > 0) {
                const maxItems = 20;
                const imagesLines = response.imageLinks.slice(0, maxItems).map((link, index) => {
                    const lastSeg = String(link).split('/').pop() || 'image';
                    const name = lastSeg.split('?')[0] || 'image';
                    return `[${index + 1}] ${name}`;
                });
                parts.push("Images:");
                parts.push(imagesLines.join('\n'));
            }
            if (response.content !== undefined) {
                parts.push(String(response.content));
            }
            return {
                platform,
                content: parts.join('\n')
            };
        }
        catch (error) {
            return {
                platform,
                content: `${platform} 图片搜索失败: ${error.message}`
            };
        }
    }
    async function search_bing(query, includeLinks = false) {
        const encodedQuery = encodeURIComponent(query);
        const url = `https://cn.bing.com/search?q=${encodedQuery}&FORM=HDRSC1`;
        return performSearch('bing', url, query, 1, includeLinks);
    }
    async function search_baidu(query, pageStr, includeLinks = false) {
        let page = 1;
        if (pageStr) {
            page = parseInt(pageStr, 10);
        }
        const pn = (page - 1) * 10;
        const encodedQuery = encodeURIComponent(query);
        const url = `https://www.baidu.com/s?wd=${encodedQuery}&pn=${pn}`;
        return performSearch('baidu', url, query, page, includeLinks);
    }
    async function search_sogou(query, pageStr, includeLinks = false) {
        let page = 1;
        if (pageStr) {
            page = parseInt(pageStr, 10);
        }
        const encodedQuery = encodeURIComponent(query);
        const url = `https://www.sogou.com/web?query=${encodedQuery}&page=${page}`;
        return performSearch('sogou', url, query, page, includeLinks);
    }
    async function search_quark(query, pageStr, includeLinks = false) {
        let page = 1;
        if (pageStr) {
            page = parseInt(pageStr, 10);
        }
        const encodedQuery = encodeURIComponent(query);
        const url = `https://quark.sm.cn/s?q=${encodedQuery}&page=${page}`;
        return performSearch('quark', url, query, page, includeLinks);
    }
    async function search_bing_images(query) {
        const encodedQuery = encodeURIComponent(query);
        const url = `https://www.bing.com/images/search?q=${encodedQuery}`;
        return performImageSearch('bing_images', url, query);
    }
    async function search_wikimedia_images(query) {
        const encodedQuery = encodeURIComponent(query);
        const url = `https://commons.wikimedia.org/wiki/Special:MediaSearch?type=image&search=${encodedQuery}`;
        return performImageSearch('wikimedia_images', url, query);
    }
    async function search_duckduckgo_images(query) {
        const encodedQuery = encodeURIComponent(query);
        const url = `https://duckduckgo.com/?q=${encodedQuery}&iax=images&ia=images`;
        return performImageSearch('duckduckgo_images', url, query);
    }
    async function search_ecosia_images(query) {
        const encodedQuery = encodeURIComponent(query);
        const url = `https://www.ecosia.org/images?q=${encodedQuery}`;
        return performImageSearch('ecosia_images', url, query);
    }
    async function search_pexels_images(query) {
        const encodedQuery = encodeURIComponent(query);
        const url = `https://www.pexels.com/search/${encodedQuery}/`;
        return performImageSearch('pexels_images', url, query);
    }
    async function search_pixabay_images(query) {
        const encodedQuery = encodeURIComponent(query);
        const url = `https://pixabay.com/images/search/${encodedQuery}/`;
        return performImageSearch('pixabay_images', url, query);
    }
    const searchFunctions = {
        bing: search_bing,
        baidu: search_baidu,
        sogou: search_sogou,
        quark: search_quark
    };
    async function combined_search(query, platforms, includeLinks = false) {
        const platformKeysRaw = platforms.split(',');
        const platformKeys = [];
        for (const platform of platformKeysRaw) {
            const trimmedPlatform = platform.trim();
            if (trimmedPlatform) {
                platformKeys.push(trimmedPlatform);
            }
        }
        const searchPromises = [];
        for (const platform of platformKeys) {
            const searchFn = searchFunctions[platform];
            if (searchFn) {
                if (platform === 'bing') {
                    searchPromises.push(searchFn(query, includeLinks));
                }
                else {
                    // 注意：这里我们假设组合搜索总是从第一页开始
                    searchPromises.push(searchFn(query, '1', includeLinks));
                }
            }
            else {
                searchPromises.push(Promise.resolve({ platform, success: false, message: `不支持的搜索平台: ${platform}` }));
            }
        }
        return Promise.all(searchPromises);
    }
    async function main() {
        const result = await combined_search('如何学习编程', 'bing,baidu,sogou,quark');
        console.log(JSON.stringify(result, null, 2));
    }
    function wrap(coreFunction, parameterNames) {
        return async (params) => {
            const args = parameterNames.map((name) => params[name]);
            return coreFunction(...args);
        };
    }
    return {
        search_bing,
        search_baidu,
        search_sogou,
        search_quark,
        search_bing_images,
        search_wikimedia_images,
        search_duckduckgo_images,
        search_ecosia_images,
        search_pexels_images,
        search_pixabay_images,
        combined_search,
        wrap,
        main
    };
})();
exports.search_bing = various_search.wrap(various_search.search_bing, ['query', 'includeLinks']);
exports.search_baidu = various_search.wrap(various_search.search_baidu, ['query', 'page', 'includeLinks']);
exports.search_sogou = various_search.wrap(various_search.search_sogou, ['query', 'page', 'includeLinks']);
exports.search_quark = various_search.wrap(various_search.search_quark, ['query', 'page', 'includeLinks']);
exports.search_bing_images = various_search.wrap(various_search.search_bing_images, ['query']);
exports.search_wikimedia_images = various_search.wrap(various_search.search_wikimedia_images, ['query']);
exports.search_duckduckgo_images = various_search.wrap(various_search.search_duckduckgo_images, ['query']);
exports.search_ecosia_images = various_search.wrap(various_search.search_ecosia_images, ['query']);
exports.search_pexels_images = various_search.wrap(various_search.search_pexels_images, ['query']);
exports.search_pixabay_images = various_search.wrap(various_search.search_pixabay_images, ['query']);
exports.combined_search = various_search.wrap(various_search.combined_search, ['query', 'platforms', 'includeLinks']);
exports.main = various_search.main;
