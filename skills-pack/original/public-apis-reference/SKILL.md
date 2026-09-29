---
name: public-apis-reference
description: Curated catalog of free and keyed public APIs relevant to the Aivance career toolkit, distilled from the public-apis/public-apis repository (cloned 2026-08-08). Use when adding or replacing an AI provider, job board, enrichment/company-data service, email verification, resume/document processing, NLP/text analysis, or productivity integration — to pick a real API, check its auth model (keyless vs apiKey vs OAuth), HTTPS support, and CORS behavior before wiring it into a provider. Also use to answer "what free APIs exist for X?" for job search, company enrichment, ATS, or career analytics.
---

# Public APIs Reference (Career Toolkit)

This skill is a **curated subset** of [public-apis/public-apis](https://github.com/public-apis/public-apis)
(the canonical list of free APIs), scoped to what a career-operating-system app
(Aivance) would actually use. The full upstream list has ~50 categories and
1,400+ APIs; only the career-relevant ones are reproduced here so the skill stays
lean and auditable. Sizes/auth were verified against the upstream README on
2026-08-08.

## How to use

1. **Pick the category** below that matches the capability you're adding
   (Jobs, AI/LLM, Enrichment, Email, Documents, NLP).
2. **Check the auth model**: `No` = keyless (works out of the box),
   `apiKey` = needs a developer key, `OAuth` = needs the user's account consent.
   For a keyless-first app, prefer `No` entries unless a keyed service is
   strictly better (the Aivance Provider SDK supports both — see
   `provider-sdk-extension`).
3. **Verify before wiring**: never assume a URL or a free tier still exists.
   Before committing a provider, do a live check (HTTP 200/302, expected JSON)
   and record it — see the `verify-before-claim` skill. The upstream list is
   community-maintained and some entries rot.
4. **Prefer HTTPS**: every entry marked `Yes` under HTTPS is safe to pin.

## Jobs (job boards & search)

| API | Description | Auth | HTTPS |
|---|---|---|---|
| [AI Dev Jobs](https://aidevboard.com/openapi.yaml) | AI/ML engineering job aggregator, REST+RSS+MCP | No | Yes |
| [Adzuna](https://developer.adzuna.com/overview) | Job board aggregator (already a provider in Aivance) | apiKey | Yes |
| [Arbeitnow](https://documenter.getpostman.com/view/18545278/UVJbJdKh) | EU / remote job aggregator (Aivance provider) | No | Yes |
| [Artificial Intelligence Jobs](https://artificialintelligencejobs.co/developers) | AI/ML listings from 260+ company career pages, salary/location/remote filters | No | Yes |
| [Careerjet](https://www.careerjet.com/partners/api/) | Job search engine | apiKey | No |
| [DevITjobs UK](https://devitjobs.uk/job_feed.xml) | Jobs with GraphQL | No | Yes |
| [Findwork](https://findwork.dev/developers/) | Job board | apiKey | Yes |
| [freehire](https://freehire.dev/docs/api) | Tech jobs aggregated from company ATS boards | No | Yes |
| [GraphQL Jobs](https://graphql.jobs/docs/api/) | Jobs with GraphQL | No | Yes |
| [HeroHunt People Search](https://www.herohunt.ai/people-search-api) | Search 1B people profiles (LinkedIn/GitHub) for sourcing | apiKey | Yes |
| [Jooble](https://jooble.org/api/about) | Job search engine | apiKey | Yes |
| [JobDataLake](https://www.jobdatalake.com/docs) | 1M+ enriched listings from 20k companies, salary/skills/seniority | apiKey | Yes |
| [Open Skills](https://github.com/workforce-data-initiative/skills-api/wiki/API-Overview) | Job titles, skills, related jobs (US DOL data) | No | No |
| [Reed](https://www.reed.co.uk/developers) | Job board aggregator | apiKey | Yes |
| [The Muse](https://www.themuse.com/developers/api/v2) | Job board + company profiles | apiKey | Yes |
| [USAJOBS](https://developer.usajobs.gov/) | US federal job board (Aivance provider) | apiKey | Yes |
| [ZipRecruiter](https://www.ziprecruiter.com/publishers) | Job search (Aivance provider) | apiKey | Yes |

## AI / Machine Learning (LLM & inference)

| API | Description | Auth | HTTPS |
|---|---|---|---|
| [Groq](https://console.groq.com/docs/quickstart) | Fast inference, free tier, Llama/Mixtral/Gemma (Aivance provider) | apiKey | Yes |
| [Hugging Face](https://huggingface.co) | Model hub + Inference API (NLP/CV/audio) | apiKey | Yes |
| [DeepAI](https://deepai.org/) | Text generation, image processing | apiKey | Yes |
| [NLP Cloud](https://nlpcloud.io) | spaCy/transformers: NER, sentiment, classification, summarization | apiKey | Yes |
| [Jina AI](https://jina.ai) | Free embeddings, reranking, text processing | apiKey | Yes |
| [Dialogflow](https://cloud.google.com/dialogflow/docs/) | NLU / conversational agents | apiKey | Yes |
| [Clarifai](https://docs.clarifai.com/api-guide/api-overview) | Computer vision | OAuth | Yes |
| [WolframAlpha](https://products.wolframalpha.com/api/) | Computed answers over curated data | apiKey | Yes |
| [Google Cloud Natural](https://cloud.google.com/natural-language/docs/) | Sentiment, entity, syntax | apiKey | Yes |
| [AI Economics Tools](https://piszczek.pl/tools/api) | Token cost / LLM energy / agent-hour calculators | No | Yes |
| [TensorFeed](https://tensorfeed.ai/developers) | AI news, model pricing, service status feeds | No | Yes |

> The biggest LLM providers (OpenAI, Anthropic, Google Gemini) are **not** in the
> upstream list — they have their own developer portals. Aivance already ships
> Gemini/Claude/OpenAI/Groq/OpenRouter/Ollama via its Provider SDK; use this
> skill to find *additional* free/niche inference endpoints, not to re-add those.

> **Notably omitted from the Jobs table** (present upstream, judged niche for a
> career toolkit): Arbeitsamt (DE), Jobs2Careers, Juju, TechRole Index (RU),
> Upwork (freelance marketplace), WhatJobs, DevITjobs UK feeds, Careerjet
> (HTTP only). Check the upstream repo if you need any of these.

## Enrichment / Business (company & people data)

| API | Description | Auth | HTTPS |
|---|---|---|---|
| [Tomba email finder](https://tomba.io/api) | Email finder + verifier for B2B sales | apiKey | Yes |
| [Village](https://docs.village.ai) | Person & company enrichment + warm-intro paths | apiKey | Yes |
| [Signaliz](https://signaliz.docs.buildwithfern.com/signaliz-api-public-docs/introduction) | GTM enrichment, lead gen, email verification, company signals | apiKey | Yes |
| [ORB Intelligence](https://api.orb-intelligence.com/docs/) | Company lookup | apiKey | Yes |
| [Clearbit Logo](https://clearbit.com/docs#logo-api) | Company logos | apiKey | Yes |
| [Funding Signals](https://fundingsignals.net/docs) | Companies that just raised (sales leads from SEC) | apiKey | No |
| [Charity Search](http://charityapi.orghunter.com/) | Non-profit charity data | apiKey | No |
| [Domainsdb.info](https://domainsdb.info/) | Registered domain search | No | Yes |

## Email (verification & delivery)

| API | Description | Auth | HTTPS |
|---|---|---|---|
| [Disify](https://www.disify.com/) | Validate + detect disposable emails | No | Yes |
| [EVA](https://eva.pingutil.com/) | Email validation | No | Yes |
| [Kickbox](https://open.kickbox.com/) | Email verification | No | Yes |
| [MailCheck.ai](https://www.mailcheck.ai/#documentation) | Block temporary-email signups | No | Yes |
| [Abstract Email Validation](https://www.abstractapi.com/email-verification-validation-api) | Deliverability + spam scoring | apiKey | Yes |
| [mailboxlayer](https://mailboxlayer.com) | Email validation | apiKey | Yes |
| [MailboxValidator](https://www.mailboxvalidator.com/api-email-free) | Deliverability validation | apiKey | Yes |
| [Sendgrid](https://docs.sendgrid.com/api-reference/) | Transactional email (SMTP/API) | apiKey | Yes |
| [Sendinblue](https://developers.sendinblue.com/docs) | Marketing + transactional email/SMS | apiKey | Yes |

## Text Analysis / NLP (ATS & resume scoring helper APIs)

| API | Description | Auth | HTTPS |
|---|---|---|---|
| [Aylien Text Analysis](https://docs.aylien.com/textapi/#getting-started) | IR + NLP suite | apiKey | Yes |
| [Cloudmersive NLP](https://www.cloudmersive.com/nlp-api) | NLP + text analysis | apiKey | Yes |
| [Detect Language](https://detectlanguage.com/) | Language detection | apiKey | Yes |
| [Sentiment Analysis](https://www.meaningcloud.com/developer/sentiment-analysis) | Multilingual sentiment | apiKey | Yes |
| [LibreTranslate](https://libretranslate.com/docs) | Translation, 17 languages | No | Yes |
| [Kiprio Translate](https://kiprio.com/v1/translate) | Translation + language detection, 50+ languages | apiKey | Yes |
| [Semantria](https://semantria.readme.io/docs) | Sentiment, categorization, NER | OAuth | Yes |
| [Perspective](https://perspectiveapi.com) | Toxicity probability scoring | apiKey | Yes |

## Documents & Productivity (resume/PDF/OCR)

| API | Description | Auth | HTTPS |
|---|---|---|---|
| [iLovePDF](https://developer.ilovepdf.com/) | Convert/merge/split/extract PDFs, free 250 docs/mo | apiKey | Yes |
| [Cloudmersive Convert](https://cloudmersive.com/convert-api) | HTML/URL→PDF/PNG, Office→PDF | apiKey | Yes |
| [CraftMyPDF](https://craftmypdf.com) | PDF from templates | apiKey | Yes |
| [OCR.Space](https://ocr.space/ocrapi) | OCR from images/PDFs, free tier | apiKey | Yes |
| [Api2Convert](https://www.api2convert.com/) | File conversion | apiKey | Yes |
| [DocStruct](https://docstruct.pages.dev) | AI extraction of invoices/receipts/contracts → JSON | No | Yes |
| [PandaDoc](https://developers.pandadoc.com) | DocGen + eSignatures | apiKey | Yes |
| [Notion](https://developers.notion.com/docs/getting-started) | Notes/CRM integration | OAuth | Yes |
| [Airtable](https://airtable.com/api) | Spreadsheet/database integration | apiKey | Yes |

## Personality / Career guidance

| API | Description | Auth | HTTPS |
|---|---|---|---|
| [Personality.fyi](https://personality.fyi/api) | Free MBTI + OEJTS test scoring | No | Yes |
| [Advice Slip](http://api.adviceslip.com/) | Random advice | No | Yes |
| [Inspiration](https://inspiration.goprogram.ai/docs/) | Motivational quotes | No | Yes |

## Aivance integration guidance

- **Job providers**: Aivance's `core:job-providers` already implements Adzuna,
  Arbeitnow, USAJobs, ZipRecruiter + keyed LinkedIn/Indeed/Greenhouse/Lever and
  keyless RemoteOK/Remotive/Jobicy. New candidates from this list: **freehire**
  (keyless, ATS-aggregated tech jobs), **Artificial Intelligence Jobs** (keyless),
  **JobDataLake** (enriched), **Open Skills** (skills taxonomy — useful for the
  Learning tab).
- **Enrichment**: Hunter.io is the current provider. Candidates: **Tomba**
  (email finder + verifier), **Village** (people/company enrichment).
- **Resume/ATS**: OCR.Space (photo→text resume import), iLovePDF
  (PDF resume conversion), Cloudmersive NLP (keyword/entity extraction) could
  backfill gaps the local parser can't cover.
- **Email outreach**: Sendgrid/Sendinblue for transactional email from the
  Recruiter CRM, with **Disify**/**Kickbox** (both keyless) as pre-send
  verification.
- **Onboarding assessments**: Personality.fyi (keyless MBTI) for the
  Prep Studio / career-guidance angle.
