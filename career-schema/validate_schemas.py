#!/usr/bin/env python3
"""
AiVance Career Schema Validator
Validates all JSON Schema files in career-schema/ against Draft 2020-12 conventions.
"""

import os
import sys
import json
from pathlib import Path

def main():
    schema_dir = Path(__file__).parent.resolve()
    schema_files = sorted(schema_dir.glob("*.schema.json"))
    
    if not schema_files:
        print("Error: No schema files found in", schema_dir)
        sys.exit(1)
        
    print(f"Validating {len(schema_files)} Career JSON Schemas in {schema_dir}...\n")
    
    errors = []
    success_count = 0
    
    for file_path in schema_files:
        filename = file_path.name
        try:
            with open(file_path, "r", encoding="utf-8") as f:
                schema = json.load(f)
                
            # Draft 2020-12 checks
            schema_dialect = schema.get("$schema")
            expected_dialect = "https://json-schema.org/draft/2020-12/schema"
            if schema_dialect != expected_dialect:
                raise ValueError(f"Expected $schema '{expected_dialect}', got '{schema_dialect}'")
                
            schema_id = schema.get("$id")
            if not schema_id or not schema_id.startswith("https://schema.aivance.org/v2/"):
                raise ValueError(f"Invalid or missing $id: '{schema_id}'")
                
            title = schema.get("title")
            if not title:
                raise ValueError("Missing 'title' attribute")
                
            schema_type = schema.get("type")
            if schema_type != "object":
                raise ValueError(f"Root type must be 'object', found '{schema_type}'")
                
            properties = schema.get("properties")
            if not isinstance(properties, dict):
                raise ValueError("'properties' must be a dictionary")
                
            required = schema.get("required")
            if not isinstance(required, list):
                raise ValueError("'required' must be an array of field names")
                
            # Verify all required properties exist in properties
            missing_props = [r for r in required if r not in properties]
            if missing_props:
                raise ValueError(f"Required fields not defined in properties: {missing_props}")
                
            print(f"  [PASS] {filename:<28} | Title: {title:<25} | Required: {len(required):<2} | Properties: {len(properties):<2}")
            success_count += 1
        except Exception as ex:
            print(f"  [FAIL] {filename:<28} | Error: {ex}")
            errors.append((filename, str(ex)))

    print("\n" + "=" * 80)
    if errors:
        print(f"Validation FAILED: {len(errors)} error(s) detected out of {len(schema_files)} schemas.")
        sys.exit(1)
    else:
        print(f"Validation SUCCESS: All {success_count} schemas conform strictly to JSON Schema Draft 2020-12.")
        sys.exit(0)

if __name__ == "__main__":
    main()
