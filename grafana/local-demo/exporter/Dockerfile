FROM python:3.12-slim
WORKDIR /app
COPY topology.py exporter.py generate_history.py ./
EXPOSE 9404
CMD ["python3", "exporter.py"]
