-- Grant all privileges in public schema to cloudsqliamuser if the role exists
DO $$
BEGIN
        IF EXISTS
            (SELECT 1 FROM pg_roles WHERE rolname = 'cloudsqliamuser')
        THEN
            -- Schema usage
            GRANT USAGE ON SCHEMA public TO cloudsqliamuser;

            -- Existing objects
            GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;
            GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO cloudsqliamuser;
            GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO cloudsqliamuser;

            -- Future objects
            ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO cloudsqliamuser;
            ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO cloudsqliamuser;
            ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO cloudsqliamuser;
END IF;
END
$$;