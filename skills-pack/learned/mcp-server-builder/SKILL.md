---
name: mcp-server-builder
description: Design and build high-quality MCP (Model Context Protocol) servers that let LLMs interact with external services through well-designed tools. Use when building MCP servers to integrate external APIs or services (Python FastMCP or TypeScript MCP SDK), or when extending an app's data/services to AI clients. Inherited from anthropics/skills (mcp-builder).
---

# MCP Server Builder

Create MCP (Model Context Protocol) servers that enable LLMs to accomplish real-world
tasks through well-designed tools. The quality of an MCP server is measured by how well
it enables an agent to do real work.

## Phase 1: Design (before code)

### API Coverage vs. Workflow Tools

Balance comprehensive API endpoint coverage with specialized workflow tools:

- **Comprehensive coverage** gives agents flexibility to compose operations.
- **Workflow tools** are convenient for specific tasks (e.g. "create a user with all
  its resources").
- When uncertain, prioritize comprehensive API coverage.

### Tool Naming and Discoverability

Clear, descriptive, action-oriented names help agents find the right tools:
- Consistent prefixes: `github_create_issue`, `github_list_repos` — never `create`,
  `make`, `do` (vague).
- The name alone should tell the agent what the tool does and returns.

### Context Management

- Concise tool descriptions — agents read them to choose tools.
- **Filter/paginate results.** Design tools that return focused, relevant data, not
  entire tables. `list_x(limit, cursor)` over `list_x_all()`.
- Summarize verbose responses server-side when a summary suffices.

### Actionable Error Messages

Errors must guide agents toward solutions with specific suggestions and next steps:

- Bad: `Error 500: server error`
- Good: `Error 400: 'status' must be one of [active, archived]. Received 'on'.`

## Phase 2: Build

### Project Structure

- **Python:** FastMCP — `from mcp.server.fastmcp import FastMCP`; register tools with
  type hints + docstrings.
- **TypeScript:** the MCP TypeScript SDK; define tools with Zod schemas.
- Keep each tool small; share auth/config via a client layer, not per-tool globals.

### Tool Signature Conventions

- Type everything. `@mcp.tool()` with typed params and return types.
- Every tool returns a structured result the agent can act on — success/failure is
  explicit, not implied by exceptions.
- Never return raw HTML or huge blobs; return structured summaries + IDs that the
  agent can use for follow-up calls.

### Streaming and Long Operations

- Long-running operations should return progress or a task handle the agent polls,
  rather than blocking the tool call.
- Respect timeouts; make cancelable operations where the underlying API supports it.

## Phase 3: Test with a Real Client

1. **Run the server locally** and connect a real MCP client.
2. **Try the tools yourself** the way an agent would: call `list_tools`, then perform a
   realistic multi-step task end-to-end. Fix anything that makes the flow awkward.
3. **Error-path tests:** feed invalid input and confirm the error messages are
   actionable.
4. **Auth tests:** token-less, expired, and scoped-down credentials all behave
   predictably.

## Phase 4: Document

- **README:** what the server does, install/run steps, how to configure auth.
- **Tools reference:** one line per tool, what it takes, what it returns.
- **Example tasks:** 2-3 realistic agent workflows, with the exact tool sequences.

## Quality Checklist

- [ ] Tool names: consistent prefix + action-oriented, self-describing
- [ ] Every tool typed, documented, returns structured results
- [ ] List endpoints paginate/filter by default
- [ ] Error messages actionable (what happened, what to do next)
- [ ] Tested end-to-end with a real MCP client
- [ ] Auth failures and invalid input handled predictably
- [ ] README with install, auth, and example workflows
