/**
 * Memory management namespace
 * Provides methods for managing the memory library
 */

/**
 * Memory management tools
 */
export namespace Memory {
    /**
     * Query the memory library
     * @param query - Search query. Supports a natural-language question, a space-separated phrase, or `|`-separated keywords. Inside a keyword, `*` works as a fuzzy wildcard placeholder such as `error*timeout`; use only `*` to return all memories
     * @param folderPath - Optional folder path to search within
     * @param limit - Optional maximum number of results (>=1, default 20)
     * @param startTime - Optional local-time start time in `YYYY-MM-DD` or `YYYY-MM-DD HH:mm` format, filters memories by createdAt >= startTime
     * @param endTime - Optional local-time end time in `YYYY-MM-DD` or `YYYY-MM-DD HH:mm` format, filters memories by createdAt <= endTime
     * @param snapshotId - Optional snapshot id. Omit or pass empty to auto-create one; pass any non-empty id to use or create that exact snapshot so follow-up or parallel queries can exclude already returned memories
     * @param threshold - Optional relevance threshold (>=0). Only memories whose score is at least this value are returned; `query_memory` defaults to `0`
     * @returns Structured query results
     */
    function query(
        query: string,
        folderPath?: string,
        limit?: number,
        startTime?: string,
        endTime?: string,
        snapshotId?: string,
        threshold?: number
    ): Promise<import('./results').MemoryQueryResultData>;

    /**
     * Get a memory by exact title
     * @param title - The exact title of the memory
     * @param chunkIndex - Optional chunk index for document nodes
     * @param chunkRange - Optional chunk range for document nodes (e.g., "3-7")
     * @param query - Optional query to search within the document. Supports natural language, space-separated phrases, `|`-separated keywords, and `*` as a fuzzy wildcard inside a keyword
     * @param limit - Optional maximum number of matched chunks when using query (>=1, default 20)
     * @returns Memory content as a string
     */
    function getByTitle(title: string, chunkIndex?: number, chunkRange?: string, query?: string, limit?: number): Promise<string>;

    /**
     * Create a new memory
     * @param title - Memory title
     * @param content - Memory content
     * @param contentType - Optional content type (default "text/plain")
     * @param source - Optional source (default "ai_created")
     * @param folderPath - Optional folder path (default "")
     * @param tags - Optional tags (comma-separated string)
     * @returns Creation result as a string
     */
    function create(title: string, content: string, contentType?: string, source?: string, folderPath?: string, tags?: string): Promise<string>;

    /**
     * Update options for memory update
     */
    interface UpdateOptions {
        newTitle?: string;
        content?: string;
        contentType?: string;
        source?: string;
        credibility?: number;
        importance?: number;
        folderPath?: string;
        tags?: string;
    }

    /**
     * Update an existing memory
     * @param oldTitle - The current title of the memory
     * @param updates - Update options
     * @returns Update result as a string
     */
    function update(oldTitle: string, updates?: UpdateOptions): Promise<string>;

    /**
     * Delete a memory
     * @param title - The title of the memory to delete
     * @returns Deletion result as a string
     */
    function deleteMemory(title: string): Promise<string>;

    /**
     * Move memories to another folder in batch
     * @param targetFolderPath - Target folder path (empty string means uncategorized)
     * @param titles - Optional memory titles (string array or comma-separated string)
     * @param sourceFolderPath - Optional source folder path (empty string means uncategorized)
     * @returns Move result as a string
     */
    function move(targetFolderPath: string, titles?: string[] | string, sourceFolderPath?: string): Promise<string>;

    /**
     * Create a link between two memories
     * @param sourceTitle - The title of the source memory
     * @param targetTitle - The title of the target memory
     * @param linkType - Optional link type (default "related")
     * @param weight - Optional link strength (0.0-1.0, default 0.7)
     * @param description - Optional description of the relationship
     * @returns Link creation result
     */
    function link(sourceTitle: string, targetTitle: string, linkType?: string, weight?: number, description?: string): Promise<import('./results').MemoryLinkResultData>;

    /**
     * Query memory links with optional filters
     * @param linkId - Optional link ID (exact match)
     * @param sourceTitle - Optional source memory title filter
     * @param targetTitle - Optional target memory title filter
     * @param linkType - Optional link type filter
     * @param limit - Optional maximum number of links to return (1-200, default 20)
     * @returns Link query result
     */
    function queryLinks(
        linkId?: number,
        sourceTitle?: string,
        targetTitle?: string,
        linkType?: string,
        limit?: number
    ): Promise<import('./results').MemoryLinkQueryResultData>;

    /**
     * Update an existing memory link
     * @param linkId - Optional link ID (preferred locator)
     * @param sourceTitle - Optional source memory title (used when linkId is not provided)
     * @param targetTitle - Optional target memory title (used when linkId is not provided)
     * @param linkType - Optional current link type (for unique resolution)
     * @param newLinkType - Optional new link type
     * @param weight - Optional new link strength (0.0-1.0)
     * @param description - Optional new relationship description
     * @returns Link update result
     */
    function updateLink(
        linkId?: number,
        sourceTitle?: string,
        targetTitle?: string,
        linkType?: string,
        newLinkType?: string,
        weight?: number,
        description?: string
    ): Promise<import('./results').MemoryLinkResultData>;

    /**
     * Delete an existing memory link
     * @param linkId - Optional link ID (preferred locator)
     * @param sourceTitle - Optional source memory title (used when linkId is not provided)
     * @param targetTitle - Optional target memory title (used when linkId is not provided)
     * @param linkType - Optional link type (for unique resolution)
     * @returns Deletion result as a string
     */
    function deleteLink(linkId?: number, sourceTitle?: string, targetTitle?: string, linkType?: string): Promise<string>;
}

